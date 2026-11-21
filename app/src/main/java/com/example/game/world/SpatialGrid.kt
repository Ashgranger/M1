package com.example.game.world

import com.example.game.config.WorldConfig
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================================
// SECTION: PHYSICS — Spatial Hash Grid, Capsule vs AABB & Raycast Engine
// ============================================================================

enum class ColliderKind {
    BUILDING_WALL,
    BUILDING_FLOOR,
    RAMP,
    VAULTABLE_WALL,
    ROCK,
    TREE_TRUNK,
    CRATE,
    BARREL
}

data class WorldAabb(
    val id: Int,
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
    val kind: ColliderKind,
    val colorRgb: FloatArray = floatArrayOf(0.5f, 0.5f, 0.55f),
    val entityRefId: Int = -1,
    val rampAxis: Int = 0, // 0 none, 1 +X slope, -1 -X slope, 2 +Z slope, -2 -Z slope
    var active: Boolean = true
) {
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f
    val sizeX: Float get() = maxX - minX
    val sizeY: Float get() = maxY - minY
    val sizeZ: Float get() = maxZ - minZ
}

data class RayHit(
    val hit: Boolean,
    val distance: Float,
    val hitX: Float,
    val hitY: Float,
    val hitZ: Float,
    val collider: WorldAabb? = null
)

class SpatialGrid(private val cellSize: Float = 28f) {

    private val cells = HashMap<Long, MutableList<WorldAabb>>(1024)
    val allColliders = ArrayList<WorldAabb>(2048)

    private fun cellKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) xor (cz.toLong() and 0xFFFFFFFFL)
    private fun toCell(v: Float): Int = floor(v / cellSize).toInt()

    fun add(aabb: WorldAabb) {
        allColliders.add(aabb)
        val x0 = toCell(aabb.minX)
        val x1 = toCell(aabb.maxX)
        val z0 = toCell(aabb.minZ)
        val z1 = toCell(aabb.maxZ)
        for (cx in x0..x1) {
            for (cz in z0..z1) {
                cells.getOrPut(cellKey(cx, cz)) { ArrayList(8) }.add(aabb)
            }
        }
    }

    fun queryNearby(x: Float, z: Float, radius: Float, out: MutableList<WorldAabb>) {
        out.clear()
        val x0 = toCell(x - radius)
        val x1 = toCell(x + radius)
        val z0 = toCell(z - radius)
        val z1 = toCell(z + radius)
        for (cx in x0..x1) {
            for (cz in z0..z1) {
                val list = cells[cellKey(cx, cz)] ?: continue
                for (i in 0 until list.size) {
                    val box = list[i]
                    if (box.active) out.add(box)
                }
            }
        }
    }

    data class CapsuleResult(
        var x: Float,
        var y: Float,
        var z: Float,
        var grounded: Boolean,
        var canVault: Boolean,
        var vaultTargetY: Float,
        var indoor: Boolean
    )

    fun resolveCapsule(
        x: Float, y: Float, z: Float,
        radius: Float, height: Float,
        terrain: TerrainGenerator,
        scratch: MutableList<WorldAabb>
    ): CapsuleResult {
        var px = x.coerceIn(-WorldConfig.HALF_WORLD + 8f, WorldConfig.HALF_WORLD - 8f)
        var pz = z.coerceIn(-WorldConfig.HALF_WORLD + 8f, WorldConfig.HALF_WORLD - 8f)
        var py = y
        val terrainY = terrain.getHeight(px, pz)
        var floorY = terrainY
        var grounded = false
        var canVault = false
        var vaultY = py
        var indoor = false

        queryNearby(px, pz, radius + 2.2f, scratch)

        for (i in 0 until scratch.size) {
            val b = scratch[i]
            if (!b.active) continue

            // Check if standing on/over floor or ramp
            val insideXZ = px + radius * 0.7f > b.minX && px - radius * 0.7f < b.maxX &&
                pz + radius * 0.7f > b.minZ && pz - radius * 0.7f < b.maxZ

            if (insideXZ) {
                if (b.kind == ColliderKind.BUILDING_FLOOR) {
                    if (py >= b.maxY - 0.65f && py <= b.maxY + 0.55f) {
                        floorY = max(floorY, b.maxY)
                        indoor = true
                    } else if (py + height > b.minY && py < b.minY) {
                        // Ceiling overhead -> indoor
                        indoor = true
                    }
                    continue
                }
                if (b.kind == ColliderKind.RAMP && b.rampAxis != 0) {
                    val t = when (b.rampAxis) {
                        1 -> ((px - b.minX) / b.sizeX.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                        -1 -> ((b.maxX - px) / b.sizeX.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                        2 -> ((pz - b.minZ) / b.sizeZ.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                        else -> ((b.maxZ - pz) / b.sizeZ.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                    }
                    val rampY = b.minY + t * b.sizeY
                    if (py >= rampY - 1.1f && py <= rampY + 1.0f) {
                        floorY = max(floorY, rampY)
                    }
                    continue
                }
            }

            // Vertical overlap check for solid walls / rocks / crates / barrels
            if (py + height <= b.minY + 0.1f || py >= b.maxY - 0.15f) {
                // Standing cleanly on top of crate/wall
                if (insideXZ && py >= b.maxY - 0.45f && py <= b.maxY + 0.35f) {
                    floorY = max(floorY, b.maxY)
                }
                continue
            }

            // Closest point on AABB XZ to capsule center
            val closestX = px.coerceIn(b.minX, b.maxX)
            val closestZ = pz.coerceIn(b.minZ, b.maxZ)
            val dx = px - closestX
            val dz = pz - closestZ
            val distSq = dx * dx + dz * dz
            if (distSq < radius * radius) {
                // Check low wall / crate vaulting opportunity
                val stepHeight = b.maxY - py
                if (stepHeight in 0.45f..1.65f && (b.kind == ColliderKind.VAULTABLE_WALL || b.kind == ColliderKind.CRATE)) {
                    canVault = true
                    vaultY = b.maxY + 0.15f
                }

                val dist = sqrt(distSq)
                if (dist > 0.0001f) {
                    val push = radius - dist
                    px += (dx / dist) * push
                    pz += (dz / dist) * push
                } else {
                    // Inside box center — push out along shallowest axis
                    val penL = abs(px - b.minX)
                    val penR = abs(b.maxX - px)
                    val penF = abs(pz - b.minZ)
                    val penB = abs(b.maxZ - pz)
                    val minPen = min(min(penL, penR), min(penF, penB))
                    when (minPen) {
                        penL -> px = b.minX - radius
                        penR -> px = b.maxX + radius
                        penF -> pz = b.minZ - radius
                        else -> pz = b.maxZ + radius
                    }
                }
            }
        }

        if (py <= floorY + 0.08f) {
            py = floorY
            grounded = true
        }

        return CapsuleResult(px, py, pz, grounded, canVault, vaultY, indoor)
    }

    fun raycastWorld(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        maxDist: Float,
        terrain: TerrainGenerator
    ): RayHit {
        var bestDist = maxDist
        var bestBox: WorldAabb? = null

        // Step along grid cells intersected by ray
        val steps = (maxDist / (cellSize * 0.5f)).toInt().coerceIn(2, 42)
        val stepDist = maxDist / steps
        var lastCx = Int.MIN_VALUE
        var lastCz = Int.MIN_VALUE

        for (s in 0..steps) {
            val d = s * stepDist
            if (d > bestDist) break
            val sx = ox + dx * d
            val sy = oy + dy * d
            val sz = oz + dz * d

            // Terrain intersection check
            if (s > 0 && sy <= terrain.getHeight(sx, sz)) {
                // Binary refine terrain hit
                var lo = (s - 1) * stepDist
                var hi = d
                for (it in 0 until 5) {
                    val mid = (lo + hi) * 0.5f
                    if (oy + dy * mid <= terrain.getHeight(ox + dx * mid, oz + dz * mid)) hi = mid else lo = mid
                }
                if (hi < bestDist) {
                    bestDist = hi
                    bestBox = null
                }
                break
            }

            val cx = toCell(sx)
            val cz = toCell(sz)
            if (cx != lastCx || cz != lastCz) {
                lastCx = cx
                lastCz = cz
                for (oxc in -1..1) {
                    for (ozc in -1..1) {
                        val list = cells[cellKey(cx + oxc, cz + ozc)] ?: continue
                        for (i in 0 until list.size) {
                            val box = list[i]
                            if (!box.active) continue
                            val t = rayIntersectsAabb(ox, oy, oz, dx, dy, dz, box, bestDist)
                            if (t in 0.01f..bestDist) {
                                bestDist = t
                                bestBox = box
                            }
                        }
                    }
                }
            }
        }

        val hit = bestDist < maxDist - 0.001f
        return RayHit(
            hit = hit,
            distance = bestDist,
            hitX = ox + dx * bestDist,
            hitY = oy + dy * bestDist,
            hitZ = oz + dz * bestDist,
            collider = bestBox
        )
    }

    private fun rayIntersectsAabb(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        b: WorldAabb, maxT: Float
    ): Float {
        var tMin = 0f
        var tMax = maxT

        if (abs(dx) < 1e-6f) {
            if (ox < b.minX || ox > b.maxX) return -1f
        } else {
            val inv = 1f / dx
            var t1 = (b.minX - ox) * inv
            var t2 = (b.maxX - ox) * inv
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return -1f
        }

        if (abs(dy) < 1e-6f) {
            if (oy < b.minY || oy > b.maxY) return -1f
        } else {
            val inv = 1f / dy
            var t1 = (b.minY - oy) * inv
            var t2 = (b.maxY - oy) * inv
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return -1f
        }

        if (abs(dz) < 1e-6f) {
            if (oz < b.minZ || oz > b.maxZ) return -1f
        } else {
            val inv = 1f / dz
            var t1 = (b.minZ - oz) * inv
            var t2 = (b.maxZ - oz) * inv
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return -1f
        }

        return tMin
    }
}
