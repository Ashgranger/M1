package com.example.game.world

import com.example.game.config.Biome
import com.example.game.config.WorldConfig
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

// ============================================================================
// SECTION: TERRAIN — Realistic PUBG/GTA Heightmap, Downtown Pad & Road Grading
// ============================================================================

class TerrainGenerator(
    val seed: Int,
    val roadNetwork: RoadNetwork = RoadNetwork()
) {

    private val perm = IntArray(512)

    // Inland lakes situated outside the central GTA downtown city footprint
    val lakes = listOf(
        floatArrayOf(-370f, 210f, 82f),  // West marsh lake
        floatArrayOf(320f, -240f, 70f),  // Alpine glacial pond
        floatArrayOf(-80f, 370f, 62f)    // Southern river bay (bridged by South Expressway)
    )

    init {
        val base = IntArray(256) { it }
        var s = seed
        for (i in 255 downTo 1) {
            s = s * 1664525 + 1013904223
            val j = ((s ushr 16) and 0x7FFF) % (i + 1)
            val tmp = base[i]
            base[i] = base[j]
            base[j] = tmp
        }
        for (i in 0 until 512) perm[i] = base[i and 255]
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun hash2(ix: Int, iz: Int): Float {
        val h = perm[(perm[ix and 255] + (iz and 255)) and 255]
        return (h / 255f) * 2f - 1f
    }

    private fun valueNoise(x: Float, z: Float): Float {
        val ix = floor(x).toInt()
        val iz = floor(z).toInt()
        val fx = fade(x - ix)
        val fz = fade(z - iz)
        val n00 = hash2(ix, iz)
        val n10 = hash2(ix + 1, iz)
        val n01 = hash2(ix, iz + 1)
        val n11 = hash2(ix + 1, iz + 1)
        return lerp(lerp(n00, n10, fx), lerp(n01, n11, fx), fz)
    }

    private fun fbm(x: Float, z: Float): Float {
        var amp = 0.52f
        var freq = 1f
        var sum = 0f
        for (oct in 0 until 4) {
            sum += valueNoise(x * freq, z * freq) * amp
            freq *= 2.08f
            amp *= 0.48f
        }
        return sum
    }

    fun getBiome(x: Float, z: Float): Biome {
        val h = getHeight(x, z)
        if (h < WorldConfig.WATER_LEVEL + 0.15f) return Biome.WATER
        val n = valueNoise(x * 0.0022f + 13.7f, z * 0.0022f - 8.4f) * 120f
        val bx = x + n
        val bz = z - n * 0.7f
        return when {
            bx >= 0f && bz >= 0f -> Biome.DESERT
            bx >= 0f && bz < 0f -> Biome.SNOW
            bx < 0f && bz >= 0f -> Biome.SWAMP
            else -> Biome.FOREST
        }
    }

    fun getHeight(x: Float, z: Float): Float {
        val half = WorldConfig.HALF_WORLD
        val nx = x / half
        val nz = z / half
        val radial = sqrt(nx * nx + nz * nz)

        val edgeMask = (1f - ((radial - 0.70f) / 0.28f).coerceIn(0f, 1f)).let { it * it * (3f - 2f * it) }
        if (edgeMask <= 0.001f) return -8f

        val baseNoise = fbm(x * 0.0024f, z * 0.0024f)
        val detail = valueNoise(x * 0.011f, z * 0.011f) * 3.4f

        val snowBoost = if (x > 160f && z < -160f) {
            val f = ((x - 160f) / 360f).coerceIn(0f, 1f) * ((-z - 160f) / 360f).coerceIn(0f, 1f)
            f * 26f * max(0f, baseNoise + 0.35f)
        } else 0f

        val swampFlatten = if (x < -160f && z > 160f) {
            val f = ((-x - 160f) / 340f).coerceIn(0f, 1f) * ((z - 160f) / 340f).coerceIn(0f, 1f)
            1f - f * 0.55f
        } else 1f

        var h = (10.0f + (baseNoise * 24f + detail) * swampFlatten + snowBoost) * edgeMask - (1f - edgeMask) * 7f

        // Carve inland lakes
        for (lake in lakes) {
            val dx = x - lake[0]
            val dz = z - lake[1]
            val d = sqrt(dx * dx + dz * dz)
            if (d < lake[2] * 1.35f) {
                val t = (1f - d / (lake[2] * 1.35f)).coerceIn(0f, 1f)
                val bowl = t * t * (3f - 2f * t)
                h = lerp(h, -3.2f, bowl)
            }
        }

        // Flatten central GTA Downtown Metro City pad cleanly
        val cityW = roadNetwork.downtownPadWeight(x, z)
        if (cityW > 0.001f) {
            h = lerp(h, roadNetwork.downtownElevation, cityW)
        }

        // Smooth road grading along outskirts highways so vehicles drive cleanly
        if (cityW < 0.99f) {
            val (roadDist, halfW) = roadNetwork.distanceToNearestRoad(x, z)
            if (roadDist < halfW + 16f && h > 0.4f) {
                val rt = (1f - ((roadDist - halfW) / 16f).coerceIn(0f, 1f)).let { it * it * (3f - 2f * it) }
                val gradedH = h.coerceIn(4.5f, 18f)
                h = lerp(h, gradedH, rt * 0.65f)
            }
        }

        return h
    }

    fun getNormal(x: Float, z: Float, out: FloatArray) {
        val e = 2.0f
        val hL = getHeight(x - e, z)
        val hR = getHeight(x + e, z)
        val hD = getHeight(x, z - e)
        val hU = getHeight(x, z + e)
        val nx = hL - hR
        val ny = 2f * e
        val nz = hD - hU
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        out[0] = nx / len
        out[1] = ny / len
        out[2] = nz / len
    }

    fun getColor(x: Float, z: Float, height: Float, ny: Float, outRgb: FloatArray) {
        // Paved Downtown concrete plaza base & highway roadbeds
        val (roadDist, halfW) = roadNetwork.distanceToNearestRoad(x, z)
        if (roadDist < halfW + 1.5f && height > 0.2f) {
            // Gritty dark weathered asphalt road surface
            val v = valueNoise(x * 0.08f, z * 0.08f) * 0.03f
            outRgb[0] = (0.19f + v).coerceIn(0.12f, 0.28f)
            outRgb[1] = (0.20f + v).coerceIn(0.13f, 0.29f)
            outRgb[2] = (0.22f + v).coerceIn(0.15f, 0.31f)
            return
        }
        if (roadNetwork.isInsideDowntown(x, z)) {
            // Urban sidewalk concrete & manicured city block ground
            val v = valueNoise(x * 0.05f, z * 0.05f) * 0.04f
            outRgb[0] = (0.44f + v).coerceIn(0.34f, 0.54f)
            outRgb[1] = (0.45f + v).coerceIn(0.35f, 0.55f)
            outRgb[2] = (0.46f + v).coerceIn(0.36f, 0.56f)
            return
        }

        if (height < 1.1f) {
            // Realistic coastal beach sand & pebbles
            outRgb[0] = 0.66f; outRgb[1] = 0.59f; outRgb[2] = 0.45f
            return
        }

        // Grounded PUBG Erangel / GTA earthy natural biome tones
        val biome = getBiome(x, z)
        when (biome) {
            Biome.FOREST -> {
                outRgb[0] = 0.28f; outRgb[1] = 0.38f; outRgb[2] = 0.21f // Olive meadow grass
            }
            Biome.DESERT -> {
                outRgb[0] = 0.64f; outRgb[1] = 0.53f; outRgb[2] = 0.36f // Miramar dusty ochre
            }
            Biome.SNOW -> {
                outRgb[0] = 0.78f; outRgb[1] = 0.82f; outRgb[2] = 0.85f // Vikendi weathered snow
            }
            Biome.SWAMP -> {
                outRgb[0] = 0.27f; outRgb[1] = 0.31f; outRgb[2] = 0.20f // Bog moss & mud
            }
            Biome.WATER -> {
                outRgb[0] = 0.14f; outRgb[1] = 0.30f; outRgb[2] = 0.42f
            }
        }

        // Steep cliff granite rock blending
        if (ny < 0.84f) {
            val rockMix = ((0.84f - ny) / 0.28f).coerceIn(0f, 0.88f)
            outRgb[0] = lerp(outRgb[0], 0.38f, rockMix)
            outRgb[1] = lerp(outRgb[1], 0.37f, rockMix)
            outRgb[2] = lerp(outRgb[2], 0.36f, rockMix)
        }
        val v = valueNoise(x * 0.028f, z * 0.028f) * 0.06f
        outRgb[0] = (outRgb[0] + v).coerceIn(0.06f, 0.94f)
        outRgb[1] = (outRgb[1] + v).coerceIn(0.06f, 0.94f)
        outRgb[2] = (outRgb[2] + v).coerceIn(0.06f, 0.94f)
    }
}
