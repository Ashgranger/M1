package com.example.game.world

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// SECTION: ROAD NETWORK — Shared GTA Downtown Street Grid, Highways & Bridges
// ============================================================================

data class RoadSegment(
    val x1: Float,
    val z1: Float,
    val x2: Float,
    val z2: Float,
    val width: Float,
    val isHighway: Boolean = false,
    val isDirt: Boolean = false
)

data class CityBlockFootprint(
    val minX: Float,
    val minZ: Float,
    val maxX: Float,
    val maxZ: Float,
    val isDowntown: Boolean = true
)

class RoadNetwork {

    val segments = ArrayList<RoadSegment>(48)
    val downtownAvenues = floatArrayOf(-180f, -60f, 60f, 180f)
    val downtownExtent = 210f
    val downtownElevation = 8.5f

    init {
        buildNetwork()
    }

    private fun buildNetwork() {
        // 1. GTA-style Dense Downtown 4x4 Avenue Grid (-200..+200)
        for (coord in downtownAvenues) {
            // East-West avenues
            segments.add(RoadSegment(-205f, coord, 205f, coord, width = 15f, isHighway = false))
            // North-South avenues
            segments.add(RoadSegment(coord, -205f, coord, 205f, width = 15f, isHighway = false))
        }

        // 2. Coastal Ring Highway Loop (12-segment highway loop around island outskirts)
        val ringRadius = 540f
        val ringNodes = 12
        for (i in 0 until ringNodes) {
            val a0 = (i.toFloat() / ringNodes) * 6.283185f
            val a1 = ((i + 1).toFloat() / ringNodes) * 6.283185f
            segments.add(
                RoadSegment(
                    x1 = cos(a0) * ringRadius,
                    z1 = sin(a0) * ringRadius,
                    x2 = cos(a1) * ringRadius,
                    z2 = sin(a1) * ringRadius,
                    width = 17f,
                    isHighway = true
                )
            )
        }

        // 3. Cardinal Expressways connecting Downtown Metro directly to Coastal Ring & POIs
        // North Expressway -> Frost Peak Alpine
        segments.add(RoadSegment(60f, -205f, 160f, -515f, width = 16f, isHighway = true))
        // East Expressway -> Military Airstrip / Dust Gulch
        segments.add(RoadSegment(205f, -60f, 520f, -60f, width = 16f, isHighway = true))
        // South-East Highway -> Dust Gulch & Solar Flats
        segments.add(RoadSegment(180f, 205f, 430f, 380f, width = 15f, isHighway = true))
        // South Expressway -> Red Barn Farm & Solar Flats (bridges over water/lowlands)
        segments.add(RoadSegment(-60f, 205f, -120f, 525f, width = 15f, isHighway = true))
        // West Expressway -> Harbor Docks & Mire Crossing
        segments.add(RoadSegment(-205f, -60f, -520f, -150f, width = 16f, isHighway = true))
        // North-West Connector -> Neon Harbor Docks
        segments.add(RoadSegment(-180f, -205f, -420f, -380f, width = 14f, isHighway = true))
        // South-West Dirt Road -> Mire Crossing
        segments.add(RoadSegment(-180f, 180f, -430f, 340f, width = 12f, isDirt = true))
        // North-East Mountain Road -> Frost Peak Village
        segments.add(RoadSegment(180f, -180f, 380f, -360f, width = 12f, isDirt = true))
    }

    /** Returns (distanceToCenterline, roadHalfWidth) of closest road segment. */
    fun distanceToNearestRoad(x: Float, z: Float): Pair<Float, Float> {
        var bestDist = Float.MAX_VALUE
        var bestHalfW = 7.5f
        for (i in 0 until segments.size) {
            val s = segments[i]
            val d = pointToSegmentDist(x, z, s.x1, s.z1, s.x2, s.z2)
            if (d < bestDist) {
                bestDist = d
                bestHalfW = s.width * 0.5f
            }
        }
        return bestDist to bestHalfW
    }

    /** Returns 0..1 weight for downtown flat urban plateau (1 inside core, fading smoothly outside). */
    fun downtownPadWeight(x: Float, z: Float): Float {
        val dx = (abs(x) - downtownExtent).coerceAtLeast(0f)
        val dz = (abs(z) - downtownExtent).coerceAtLeast(0f)
        val outside = sqrt(dx * dx + dz * dz)
        if (outside <= 0f) return 1f
        val blend = 85f
        val t = (1f - outside / blend).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun isInsideDowntown(x: Float, z: Float): Boolean =
        abs(x) <= downtownExtent && abs(z) <= downtownExtent

    fun gridCodeFor(x: Float, z: Float): String {
        val col = (((x + 1000f) / 2000f) * 8f).toInt().coerceIn(0, 7)
        val row = (((z + 1000f) / 2000f) * 8f).toInt().coerceIn(0, 7)
        val colChar = ('A'.code + col).toChar()
        return "$colChar${row + 1}"
    }

    private fun pointToSegmentDist(px: Float, pz: Float, x1: Float, z1: Float, x2: Float, z2: Float): Float {
        val dx = x2 - x1
        val dz = z2 - z1
        val lenSq = dx * dx + dz * dz
        if (lenSq < 0.0001f) return hypot(px - x1, pz - z1)
        val t = (((px - x1) * dx + (pz - z1) * dz) / lenSq).coerceIn(0f, 1f)
        val cx = x1 + t * dx
        val cz = z1 + t * dz
        return hypot(px - cx, pz - cz)
    }
}
