package com.example.game.world

import com.example.game.config.AmmoType
import com.example.game.config.AttachmentType
import com.example.game.config.Biome
import com.example.game.config.ConsumableType
import com.example.game.config.GrenadeType
import com.example.game.config.Rarity
import com.example.game.config.WeaponClass
import com.example.game.entities.BreakableCrate
import com.example.game.entities.BushProp
import com.example.game.entities.ExplosiveBarrel
import com.example.game.entities.LootKind
import com.example.game.entities.VehicleEntity
import com.example.game.entities.WorldLootItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ============================================================================
// SECTION: WORLD & CITY — Dense GTA Downtown, Highways, Bridges & PUBG Outskirts
// ============================================================================

data class PointOfInterest(
    val name: String,
    val x: Float,
    val z: Float,
    val radius: Float,
    val highTierLoot: Boolean = false
)

data class InstancedProp(
    val x: Float,
    val y: Float,
    val z: Float,
    val scaleX: Float,
    val scaleY: Float,
    val scaleZ: Float,
    val rotY: Float,
    val r: Float,
    val g: Float,
    val b: Float
)

class GameWorld(val seed: Int) {
    val roadNetwork = RoadNetwork()
    val terrain = TerrainGenerator(seed, roadNetwork)
    val spatialGrid = SpatialGrid(28f)

    val pois = ArrayList<PointOfInterest>(9)
    val buildingFootprints = ArrayList<CityBlockFootprint>(64)
    val streetLamps = ArrayList<FloatArray>(48) // x, y, z
    val treeTrunks = ArrayList<InstancedProp>(600)
    val treeCanopies = ArrayList<InstancedProp>(600)
    val rocks = ArrayList<InstancedProp>(260)
    val bushes = ArrayList<BushProp>(220)
    val crates = ArrayList<BreakableCrate>(140)
    val barrels = ArrayList<ExplosiveBarrel>(90)
    val vehicles = ArrayList<VehicleEntity>(22)
    val lootItems = ArrayList<WorldLootItem>(460)
    val navWaypoints = ArrayList<FloatArray>(128)

    private var nextColliderId = 1
    private var nextLootId = 1

    init {
        buildWorld()
    }

    private fun buildWorld() {
        val rng = Random(seed * 7919 + 42)

        // Named POIs: Central GTA Downtown Metro + 7 PUBG-inspired Outskirts Towns/Military/Farm
        pois.add(PointOfInterest("Downtown Metro", 0f, 0f, 210f, highTierLoot = true))
        pois.add(PointOfInterest("Fort Apex Base", 500f, -60f, 130f, highTierLoot = true))
        pois.add(PointOfInterest("Harbor Docks", -440f, -380f, 115f))
        pois.add(PointOfInterest("Frost Peak", 380f, -360f, 110f))
        pois.add(PointOfInterest("Dust Gulch", 420f, 360f, 115f))
        pois.add(PointOfInterest("Solar Flats", 140f, 490f, 100f))
        pois.add(PointOfInterest("Mire Docks", -430f, 340f, 105f))
        pois.add(PointOfInterest("Red Barn Farm", -125f, 515f, 95f))

        // 1. Build Dense GTA-Style Downtown Metro City (9 city blocks inside 4x4 avenues!)
        buildDowntownMetro(rng)

        // 2. Build Concrete Highway Bridge over Southern River Lake
        buildHighwayBridge(-86f, 315f, -108f, 435f)

        // 3. Build Outskirts POIs (Towns, Military Airstrip Hangars, Shipping Docks, Farm)
        for (poi in pois) {
            if (poi.name == "Downtown Metro") continue
            navWaypoints.add(floatArrayOf(poi.x, poi.z))
            val isMilitary = poi.name == "Fort Apex Base"
            val isFarm = poi.name == "Red Barn Farm"
            val bCount = if (isMilitary) 5 else if (isFarm) 3 else 4

            for (i in 0 until bCount) {
                val angle = (i.toFloat() / bCount) * (2f * PI.toFloat()) + rng.nextFloat() * 0.35f
                val dist = if (i == 0 && isMilitary) 0f else (28f + rng.nextFloat() * (poi.radius * 0.52f))
                val bx = poi.x + cos(angle) * dist
                val bz = poi.z + sin(angle) * dist
                val (roadD, roadHalf) = roadNetwork.distanceToNearestRoad(bx, bz)
                if (roadD < roadHalf + 11f) continue
                val by = terrain.getHeight(bx, bz)
                if (by < 1.3f) continue

                val w = if (isMilitary && i < 2) 20f else if (isFarm && i == 0) 18f else 13f + rng.nextFloat() * 5f
                val d = if (isMilitary && i < 2) 15f else if (isFarm && i == 0) 14f else 11f + rng.nextFloat() * 5f
                val floors = if (isMilitary && i == 2) 2 else 1
                val wallRgb = when {
                    isFarm -> floatArrayOf(0.56f, 0.20f, 0.16f) // Weathered red barn wood
                    isMilitary -> floatArrayOf(0.34f, 0.37f, 0.33f) // Olive military concrete
                    terrain.getBiome(bx, bz) == Biome.DESERT -> floatArrayOf(0.68f, 0.57f, 0.44f) // Sunbaked stucco
                    terrain.getBiome(bx, bz) == Biome.SNOW -> floatArrayOf(0.54f, 0.57f, 0.60f) // Alpine stone
                    else -> floatArrayOf(0.54f, 0.43f, 0.36f) // Brick & plaster town house
                }
                buildEnterableBuilding(bx, by, bz, w, d, 4.5f, floors, wallRgb, poi.highTierLoot, isDowntown = false, rng = rng)
                navWaypoints.add(floatArrayOf(bx, bz + d * 0.5f + 5f))
            }

            // Spawn realistic off-road trucks / sedans near each outskirts POI
            val vCount = if (isMilitary) 3 else 2
            for (v in 0 until vCount) {
                val va = rng.nextFloat() * 6.28f
                val vx = poi.x + cos(va) * (poi.radius * 0.55f)
                val vz = poi.z + sin(va) * (poi.radius * 0.55f)
                val vy = terrain.getHeight(vx, vz)
                if (vy > 1.2f) {
                    val col = pickVehicleColor(isMilitary, rng)
                    vehicles.add(VehicleEntity(vehicles.size, vx, vy, vz, rng.nextFloat() * 6.28f, colorRgb = col))
                }
            }
        }

        // Road & highway AI navigation waypoints
        for (seg in roadNetwork.segments) {
            navWaypoints.add(floatArrayOf((seg.x1 + seg.x2) * 0.5f, (seg.z1 + seg.z2) * 0.5f))
        }

        // Instanced Trees (~500) strictly outside Downtown Metro & paved road corridors
        var attempts = 0
        while (treeTrunks.size < 500 && attempts < 2600) {
            attempts++
            val tx = (rng.nextFloat() * 2f - 1f) * 860f
            val tz = (rng.nextFloat() * 2f - 1f) * 860f
            if (roadNetwork.isInsideDowntown(tx, tz)) continue
            val (rd, rh) = roadNetwork.distanceToNearestRoad(tx, tz)
            if (rd < rh + 7f) continue
            val ty = terrain.getHeight(tx, tz)
            if (ty < 1.8f || isInsidePoiCore(tx, tz, 0.68f)) continue
            val biome = terrain.getBiome(tx, tz)
            if (biome == Biome.DESERT && rng.nextFloat() > 0.18f) continue

            val scale = 0.9f + rng.nextFloat() * 0.85f
            val trunkH = 4.5f * scale
            val canopyR = 2.7f * scale
            val trunkRgb = floatArrayOf(0.32f, 0.23f, 0.15f)
            val canopyRgb = when (biome) {
                Biome.FOREST -> floatArrayOf(0.18f, 0.34f + rng.nextFloat() * 0.08f, 0.16f)
                Biome.DESERT -> floatArrayOf(0.28f, 0.36f, 0.19f)
                Biome.SNOW -> floatArrayOf(0.58f, 0.66f, 0.68f)
                Biome.SWAMP -> floatArrayOf(0.20f, 0.27f, 0.15f)
                Biome.WATER -> floatArrayOf(0.18f, 0.32f, 0.16f)
            }
            treeTrunks.add(InstancedProp(tx, ty + trunkH * 0.5f, tz, 0.60f * scale, trunkH, 0.60f * scale, rng.nextFloat() * 6.28f, trunkRgb[0], trunkRgb[1], trunkRgb[2]))
            treeCanopies.add(InstancedProp(tx, ty + trunkH + canopyR * 0.6f, tz, canopyR, canopyR * 1.55f, canopyR, rng.nextFloat() * 6.28f, canopyRgb[0], canopyRgb[1], canopyRgb[2]))
            spatialGrid.add(
                WorldAabb(
                    nextColliderId++, tx - 0.5f * scale, ty, tz - 0.5f * scale,
                    tx + 0.5f * scale, ty + trunkH, tz + 0.5f * scale,
                    ColliderKind.TREE_TRUNK, trunkRgb
                )
            )
        }

        // Instanced Rocks (~220) outside roads & downtown
        attempts = 0
        while (rocks.size < 220 && attempts < 1200) {
            attempts++
            val rx = (rng.nextFloat() * 2f - 1f) * 860f
            val rz = (rng.nextFloat() * 2f - 1f) * 860f
            if (roadNetwork.isInsideDowntown(rx, rz)) continue
            val (rd, rh) = roadNetwork.distanceToNearestRoad(rx, rz)
            if (rd < rh + 6f) continue
            val ry = terrain.getHeight(rx, rz)
            if (ry < 1.3f || isInsidePoiCore(rx, rz, 0.5f)) continue
            val sx = 1.6f + rng.nextFloat() * 2.8f
            val sy = 1.2f + rng.nextFloat() * 2.2f
            val sz = 1.6f + rng.nextFloat() * 2.8f
            val shade = 0.36f + rng.nextFloat() * 0.14f
            rocks.add(InstancedProp(rx, ry + sy * 0.42f, rz, sx, sy, sz, rng.nextFloat() * 6.28f, shade, shade * 0.99f, shade * 0.96f))
            spatialGrid.add(
                WorldAabb(
                    nextColliderId++, rx - sx * 0.45f, ry, rz - sz * 0.45f,
                    rx + sx * 0.45f, ry + sy * 0.85f, rz + sz * 0.45f,
                    ColliderKind.ROCK, floatArrayOf(shade, shade, shade)
                )
            )
        }

        // Concealment Bushes (~200)
        attempts = 0
        while (bushes.size < 200 && attempts < 1000) {
            attempts++
            val bx = (rng.nextFloat() * 2f - 1f) * 840f
            val bz = (rng.nextFloat() * 2f - 1f) * 840f
            if (roadNetwork.isInsideDowntown(bx, bz)) continue
            val (rd, rh) = roadNetwork.distanceToNearestRoad(bx, bz)
            if (rd < rh + 4f) continue
            val by = terrain.getHeight(bx, bz)
            if (by < 1.5f) continue
            bushes.add(BushProp(bx, by, bz, 2.2f + rng.nextFloat() * 0.6f))
        }

        // Outdoor Breakable Crates & Explosive Red Barrels around POIs & city alleys
        for (i in 0 until 72) {
            val poi = pois[i % pois.size]
            val ang = rng.nextFloat() * 6.28f
            val dist = 16f + rng.nextFloat() * (poi.radius * 0.72f)
            val cx = poi.x + cos(ang) * dist
            val cz = poi.z + sin(ang) * dist
            val (rd, rh) = roadNetwork.distanceToNearestRoad(cx, cz)
            if (rd < rh + 2.5f) continue
            val cy = terrain.getHeight(cx, cz)
            if (cy < 1.2f) continue
            addCrate(cx, cy, cz)
        }
        for (i in 0 until 64) {
            val poi = pois[i % pois.size]
            val ang = rng.nextFloat() * 6.28f
            val dist = 14f + rng.nextFloat() * (poi.radius * 0.78f)
            val bx = poi.x + cos(ang) * dist
            val bz = poi.z + sin(ang) * dist
            val (rd, rh) = roadNetwork.distanceToNearestRoad(bx, bz)
            if (rd < rh + 2.5f) continue
            val by = terrain.getHeight(bx, bz)
            if (by < 1.2f) continue
            addBarrel(bx, by, bz)
        }
    }

    private fun buildDowntownMetro(rng: Random) {
        val baseY = roadNetwork.downtownElevation
        val av = roadNetwork.downtownAvenues // -180, -60, 60, 180
        val sidewalkRgb = floatArrayOf(0.56f, 0.57f, 0.58f)

        // Urban architectural material palette (GTA Los Santos / PUBG Yasnaya)
        val glassTowerRgb = floatArrayOf(0.28f, 0.38f, 0.46f)
        val concreteMidRgb = floatArrayOf(0.62f, 0.60f, 0.56f)
        val redBrickRgb = floatArrayOf(0.55f, 0.29f, 0.24f)
        val slateOfficeRgb = floatArrayOf(0.36f, 0.39f, 0.42f)
        val tanStuccoRgb = floatArrayOf(0.68f, 0.62f, 0.52f)

        // 9 City Blocks bounded by avenues
        for (bxIdx in 0 until av.size - 1) {
            for (bzIdx in 0 until av.size - 1) {
                val blockMinX = av[bxIdx] + 10.5f
                val blockMaxX = av[bxIdx + 1] - 10.5f
                val blockMinZ = av[bzIdx] + 10.5f
                val blockMaxZ = av[bzIdx + 1] - 10.5f
                val blockCx = (blockMinX + blockMaxX) * 0.5f
                val blockCz = (blockMinZ + blockMaxZ) * 0.5f

                // Raised concrete sidewalk slab for the entire city block
                spatialGrid.add(
                    WorldAabb(
                        nextColliderId++, blockMinX, baseY - 0.5f, blockMinZ,
                        blockMaxX, baseY + 0.22f, blockMaxZ,
                        ColliderKind.BUILDING_FLOOR, sidewalkRgb
                    )
                )

                // Place 4 streetlamps at block corners
                streetLamps.add(floatArrayOf(blockMinX + 1.5f, baseY + 0.22f, blockMinZ + 1.5f))
                streetLamps.add(floatArrayOf(blockMaxX - 1.5f, baseY + 0.22f, blockMaxZ - 1.5f))

                // Central Plaza Block (1,1): High-rise financial tower + tactical cover plaza
                val isCenterBlock = (bxIdx == 1 && bzIdx == 1)
                if (isCenterBlock) {
                    buildEnterableBuilding(
                        blockCx, baseY + 0.22f, blockCz, 28f, 24f, 4.8f, floors = 3,
                        wallRgb = glassTowerRgb, highTier = true, isDowntown = true, rng = rng
                    )
                    // Tactical jersey barriers around plaza
                    spatialGrid.add(WorldAabb(nextColliderId++, blockCx - 22f, baseY + 0.22f, blockCz - 4f, blockCx - 20.8f, baseY + 1.35f, blockCz + 4f, ColliderKind.VAULTABLE_WALL, concreteMidRgb))
                    spatialGrid.add(WorldAabb(nextColliderId++, blockCx + 20.8f, baseY + 0.22f, blockCz - 4f, blockCx + 22f, baseY + 1.35f, blockCz + 4f, ColliderKind.VAULTABLE_WALL, concreteMidRgb))
                } else {
                    // Dense 2-to-3 building urban block (apartments, offices, shops)
                    val b1x = blockCx - 22f
                    val b1z = blockCz - 18f
                    val b2x = blockCx + 21f
                    val b2z = blockCz + 17f
                    val mat1 = when ((bxIdx + bzIdx) % 4) {
                        0 -> redBrickRgb
                        1 -> concreteMidRgb
                        2 -> slateOfficeRgb
                        else -> tanStuccoRgb
                    }
                    val mat2 = when ((bxIdx * 2 + bzIdx) % 3) {
                        0 -> glassTowerRgb
                        1 -> redBrickRgb
                        else -> concreteMidRgb
                    }
                    buildEnterableBuilding(b1x, baseY + 0.22f, b1z, 22f, 18f, 4.6f, floors = 2, wallRgb = mat1, highTier = true, isDowntown = true, rng = rng)
                    buildEnterableBuilding(b2x, baseY + 0.22f, b2z, 20f, 18f, 4.5f, floors = if ((bxIdx + bzIdx) % 2 == 0) 2 else 1, wallRgb = mat2, highTier = false, isDowntown = true, rng = rng)
                }

                // Parked city vehicle along the avenue curb
                if ((bxIdx + bzIdx) % 2 == 0) {
                    val vx = av[bxIdx] + 4.5f
                    val vz = blockCz
                    vehicles.add(VehicleEntity(vehicles.size, vx, baseY, vz, 0f, colorRgb = pickVehicleColor(false, rng)))
                }
                navWaypoints.add(floatArrayOf(blockCx, av[bzIdx]))
            }
        }
    }

    private fun buildHighwayBridge(x1: Float, z1: Float, x2: Float, z2: Float) {
        val deckY = 4.2f
        val minX = minOf(x1, x2) - 8f
        val maxX = maxOf(x1, x2) + 8f
        val minZ = minOf(z1, z2)
        val maxZ = maxOf(z1, z2)
        val concrete = floatArrayOf(0.46f, 0.47f, 0.49f)
        val rail = floatArrayOf(0.32f, 0.34f, 0.36f)
        // Walkable / drivable concrete bridge span
        spatialGrid.add(WorldAabb(nextColliderId++, minX, deckY - 1.0f, minZ, maxX, deckY, maxZ, ColliderKind.BUILDING_FLOOR, concrete))
        // Side guardrails (vaultable)
        spatialGrid.add(WorldAabb(nextColliderId++, minX, deckY, minZ, minX + 0.6f, deckY + 1.15f, maxZ, ColliderKind.VAULTABLE_WALL, rail))
        spatialGrid.add(WorldAabb(nextColliderId++, maxX - 0.6f, deckY, minZ, maxX, deckY + 1.15f, maxZ, ColliderKind.VAULTABLE_WALL, rail))
    }

    private fun pickVehicleColor(military: Boolean, rng: Random): FloatArray = when {
        military -> floatArrayOf(0.28f, 0.33f, 0.23f) // Military olive drab
        else -> when (rng.nextInt(5)) {
            0 -> floatArrayOf(0.18f, 0.20f, 0.24f) // Matte charcoal SUV
            1 -> floatArrayOf(0.58f, 0.18f, 0.16f) // GTA crimson sedan
            2 -> floatArrayOf(0.76f, 0.74f, 0.70f) // Urban white truck
            3 -> floatArrayOf(0.22f, 0.34f, 0.46f) // Steel blue cruiser
            else -> floatArrayOf(0.62f, 0.50f, 0.28f) // Desert tan 4x4
        }
    }

    private fun isInsidePoiCore(x: Float, z: Float, factor: Float): Boolean {
        for (p in pois) {
            val dx = x - p.x
            val dz = z - p.z
            val r = p.radius * factor
            if (dx * dx + dz * dz < r * r) return true
        }
        return false
    }

    private fun buildEnterableBuilding(
        cx: Float, baseY: Float, cz: Float,
        w: Float, d: Float, floorH: Float,
        floors: Int,
        wallRgb: FloatArray,
        highTier: Boolean,
        isDowntown: Boolean,
        rng: Random
    ) {
        val hw = w * 0.5f
        val hd = d * 0.5f
        val t = 0.55f
        val floorRgb = floatArrayOf(0.32f, 0.33f, 0.35f)
        val roofRgb = floatArrayOf(0.24f, 0.25f, 0.28f)
        val trimRgb = floatArrayOf(wallRgb[0] * 0.72f, wallRgb[1] * 0.72f, wallRgb[2] * 0.75f)

        buildingFootprints.add(CityBlockFootprint(cx - hw, cz - hd, cx + hw, cz + hd, isDowntown))

        val groundTop = baseY + 0.45f
        spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, baseY - 2.5f, cz - hd, cx + hw, groundTop, cz + hd, ColliderKind.BUILDING_FLOOR, floorRgb))

        val doorHalf = 1.55f
        var curFloorY = groundTop

        for (f in 0 until floors) {
            val wallTop = curFloorY + floorH
            val winBottom = curFloorY + 1.15f
            val winTop = curFloorY + 2.75f

            // South & North walls with entry doorways on ground floor / windows above
            if (f == 0) {
                spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz + hd - t, cx - doorHalf, wallTop, cz + hd, ColliderKind.BUILDING_WALL, wallRgb))
                spatialGrid.add(WorldAabb(nextColliderId++, cx + doorHalf, curFloorY, cz + hd - t, cx + hw, wallTop, cz + hd, ColliderKind.BUILDING_WALL, wallRgb))
                spatialGrid.add(WorldAabb(nextColliderId++, cx - doorHalf, curFloorY + 2.9f, cz + hd - t, cx + doorHalf, wallTop, cz + hd, ColliderKind.BUILDING_WALL, trimRgb))

                spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz - hd, cx - doorHalf, wallTop, cz - hd + t, ColliderKind.BUILDING_WALL, wallRgb))
                spatialGrid.add(WorldAabb(nextColliderId++, cx + doorHalf, curFloorY, cz - hd, cx + hw, wallTop, cz - hd + t, ColliderKind.BUILDING_WALL, wallRgb))
                spatialGrid.add(WorldAabb(nextColliderId++, cx - doorHalf, curFloorY + 2.9f, cz - hd, cx + doorHalf, wallTop, cz - hd + t, ColliderKind.BUILDING_WALL, trimRgb))
            } else {
                // Upper floor panoramic tactical windows
                spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz + hd - t, cx + hw, winBottom, cz + hd, ColliderKind.VAULTABLE_WALL, wallRgb))
                spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, winTop, cz + hd - t, cx + hw, wallTop, cz + hd, ColliderKind.BUILDING_WALL, trimRgb))
                spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz - hd, cx + hw, winBottom, cz - hd + t, ColliderKind.VAULTABLE_WALL, wallRgb))
                spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, winTop, cz - hd, cx + hw, wallTop, cz - hd + t, ColliderKind.BUILDING_WALL, trimRgb))
            }

            // East & West walls with windows
            spatialGrid.add(WorldAabb(nextColliderId++, cx + hw - t, curFloorY, cz - hd, cx + hw, wallTop, cz - 1.8f, ColliderKind.BUILDING_WALL, wallRgb))
            spatialGrid.add(WorldAabb(nextColliderId++, cx + hw - t, curFloorY, cz + 1.8f, cx + hw, wallTop, cz + hd, ColliderKind.BUILDING_WALL, wallRgb))
            spatialGrid.add(WorldAabb(nextColliderId++, cx + hw - t, curFloorY, cz - 1.8f, cx + hw, winBottom, cz + 1.8f, ColliderKind.VAULTABLE_WALL, wallRgb))
            spatialGrid.add(WorldAabb(nextColliderId++, cx + hw - t, winTop, cz - 1.8f, cx + hw, wallTop, cz + 1.8f, ColliderKind.BUILDING_WALL, trimRgb))

            spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz - hd, cx - hw + t, wallTop, cz - 1.8f, ColliderKind.BUILDING_WALL, wallRgb))
            spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz + 1.8f, cx - hw + t, wallTop, cz + hd, ColliderKind.BUILDING_WALL, wallRgb))
            spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz - 1.8f, cx - hw + t, winBottom, cz + 1.8f, ColliderKind.VAULTABLE_WALL, wallRgb))
            spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, winTop, cz - 1.8f, cx - hw + t, wallTop, cz + 1.8f, ColliderKind.BUILDING_WALL, trimRgb))

            // Interior loot room partition on ground floor
            if (f == 0) {
                spatialGrid.add(WorldAabb(nextColliderId++, cx - hw + t, curFloorY, cz - 0.3f, cx - 1.2f, wallTop - 0.1f, cz + 0.3f, ColliderKind.BUILDING_WALL, trimRgb))
            }

            // Ceiling / next floor slab
            val nextTop = wallTop + 0.42f
            spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, wallTop, cz - hd, cx + hw, nextTop, cz + hd, ColliderKind.BUILDING_FLOOR, if (f == floors - 1) roofRgb else floorRgb))

            // Exterior stairwell ramp connecting each floor cleanly up to the roof!
            val rampSide = if (f % 2 == 0) 1 else -1
            if (rampSide == 1) {
                spatialGrid.add(
                    WorldAabb(
                        nextColliderId++, cx + hw, curFloorY - 0.2f, cz - 1.8f,
                        cx + hw + 6.5f, nextTop, cz + 1.8f, ColliderKind.RAMP, floorRgb, rampAxis = -1
                    )
                )
            } else {
                spatialGrid.add(
                    WorldAabb(
                        nextColliderId++, cx - hw - 6.5f, curFloorY - 0.2f, cz - 1.8f,
                        cx - hw, nextTop, cz + 1.8f, ColliderKind.RAMP, floorRgb, rampAxis = 1
                    )
                )
            }

            // Loot spawns per floor
            spawnRandomLoot(cx - hw * 0.45f, curFloorY + 0.35f, cz - hd * 0.42f, highTier, rng)
            spawnRandomLoot(cx + hw * 0.42f, curFloorY + 0.35f, cz + hd * 0.38f, highTier, rng)
            curFloorY = nextTop
        }

        // Rooftop sniper parapet cover + roof loot
        spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz - hd, cx + hw, curFloorY + 1.08f, cz - hd + 0.4f, ColliderKind.VAULTABLE_WALL, trimRgb))
        spatialGrid.add(WorldAabb(nextColliderId++, cx - hw, curFloorY, cz + hd - 0.4f, cx + hw, curFloorY + 1.08f, cz + hd, ColliderKind.VAULTABLE_WALL, trimRgb))
        spawnRandomLoot(cx, curFloorY + 0.35f, cz, highTier, rng)

        if (rng.nextFloat() < 0.6f) {
            addCrate(cx + hw * 0.42f, groundTop, cz - hd * 0.42f)
        }
    }

    private fun addCrate(x: Float, y: Float, z: Float) {
        val cid = nextColliderId++
        val crateId = crates.size
        val col = floatArrayOf(0.54f, 0.39f, 0.22f)
        spatialGrid.add(WorldAabb(cid, x - 0.75f, y, z - 0.75f, x + 0.75f, y + 1.35f, z + 0.75f, ColliderKind.CRATE, col, entityRefId = crateId))
        crates.add(BreakableCrate(crateId, cid, x, y, z))
    }

    private fun addBarrel(x: Float, y: Float, z: Float) {
        val cid = nextColliderId++
        val barrelId = barrels.size
        val col = floatArrayOf(0.76f, 0.16f, 0.14f)
        spatialGrid.add(WorldAabb(cid, x - 0.55f, y, z - 0.55f, x + 0.55f, y + 1.45f, z + 0.55f, ColliderKind.BARREL, col, entityRefId = barrelId))
        barrels.add(ExplosiveBarrel(barrelId, cid, x, y, z))
    }

    fun rollRarity(highTier: Boolean, rng: Random): Rarity {
        val roll = rng.nextInt(100)
        return if (highTier) {
            when {
                roll < 18 -> Rarity.LEGENDARY
                roll < 44 -> Rarity.EPIC
                roll < 76 -> Rarity.RARE
                else -> Rarity.UNCOMMON
            }
        } else {
            when {
                roll < 5 -> Rarity.LEGENDARY
                roll < 16 -> Rarity.EPIC
                roll < 36 -> Rarity.RARE
                roll < 64 -> Rarity.UNCOMMON
                else -> Rarity.COMMON
            }
        }
    }

    fun spawnRandomLoot(x: Float, y: Float, z: Float, highTier: Boolean, rng: Random) {
        val category = rng.nextInt(100)
        val rarity = rollRarity(highTier, rng)
        val item = when {
            category < 36 -> {
                val wc = WeaponClass.entries[rng.nextInt(WeaponClass.entries.size)]
                WorldLootItem(nextLootId++, x, y, z, LootKind.WEAPON, rarity, weaponClass = wc)
            }
            category < 54 -> {
                val at = AmmoType.entries[rng.nextInt(AmmoType.entries.size)]
                WorldLootItem(nextLootId++, x, y, z, LootKind.AMMO, Rarity.COMMON, ammoType = at, ammoAmount = at.pickupAmount)
            }
            category < 72 -> {
                val c = ConsumableType.entries[rng.nextInt(ConsumableType.entries.size)]
                val r = if (c == ConsumableType.MEDKIT || c == ConsumableType.BIG_SHIELD) Rarity.RARE else Rarity.UNCOMMON
                WorldLootItem(nextLootId++, x, y, z, LootKind.CONSUMABLE, r, consumable = c)
            }
            category < 80 -> {
                val att = AttachmentType.entries[rng.nextInt(AttachmentType.entries.size)]
                WorldLootItem(nextLootId++, x, y, z, LootKind.ATTACHMENT, Rarity.RARE, attachment = att)
            }
            category < 87 -> {
                val g = GrenadeType.entries[rng.nextInt(GrenadeType.entries.size)]
                WorldLootItem(nextLootId++, x, y, z, LootKind.GRENADE, Rarity.UNCOMMON, grenade = g)
            }
            else -> {
                // Boost Armor/Helmet/Backpack spawn rate so visible PUBG tactical gear progression shines!
                val lvl = if (highTier) rng.nextInt(2, 4) else rng.nextInt(1, 4)
                val gearR = when (lvl) { 3 -> Rarity.EPIC; 2 -> Rarity.RARE; else -> Rarity.UNCOMMON }
                val gearKind = when (rng.nextInt(3)) {
                    0 -> LootKind.ARMOR
                    1 -> LootKind.HELMET
                    else -> LootKind.BACKPACK
                }
                WorldLootItem(nextLootId++, x, y, z, gearKind, gearR, gearLevel = lvl)
            }
        }
        lootItems.add(item)
    }

    fun isInsideBush(x: Float, z: Float): Boolean {
        for (i in 0 until bushes.size) {
            val b = bushes[i]
            val dx = x - b.x
            val dz = z - b.z
            if (dx * dx + dz * dz <= b.radius * b.radius) return true
        }
        return false
    }
}
