package com.example.game.engine

import com.example.game.config.BotPersonality
import com.example.game.config.WorldConfig
import com.example.game.entities.Actor
import com.example.game.entities.BotAiState
import com.example.game.entities.DropPhase
import com.example.game.world.GameWorld
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ============================================================================
// SECTION: AI — 29-Bot Staggered FSM, LOS, Sound Hearing, Navigation & Combat
// ============================================================================

class BotAiController(
    private val world: GameWorld,
    private val rng: Random
) {
    private var batchCounter = 0

    fun notifyGunshot(shooterId: Int, x: Float, z: Float, radius: Float, actors: List<Actor>) {
        val rSq = radius * radius
        for (i in 0 until actors.size) {
            val bot = actors[i]
            if (bot.isHuman || !bot.alive || bot.id == shooterId || bot.dropPhase != DropPhase.LANDED) continue
            val dx = x - bot.x
            val dz = z - bot.z
            if (dx * dx + dz * dz <= rSq) {
                bot.aiHeardX = x
                bot.aiHeardZ = z
                bot.aiHeardTimer = 7f
            }
        }
    }

    fun updateBots(
        dt: Float,
        actors: List<Actor>,
        human: Actor,
        stormX: Float, stormZ: Float, stormRadius: Float,
        engine: MatchEngine
    ) {
        batchCounter = (batchCounter + 1) % 3

        for (i in 0 until actors.size) {
            val bot = actors[i]
            if (bot.isHuman || !bot.alive) continue

            // Plane & parachute phases update smoothly every tick
            if (bot.dropPhase != DropPhase.LANDED) {
                updateBotDrop(bot, dt, engine)
                continue
            }

            // Distance to human (for LOD AI simplification)
            val distHumanSq = distSq(bot.x, bot.z, human.x, human.z)
            val isFar = distHumanSq > 260f * 260f

            // Staggered tactical decision ticks (3 batches -> ~20 Hz thinking)
            val shouldThink = (i % 3 == batchCounter)
            if (shouldThink) {
                if (isFar) {
                    thinkFarBot(bot, stormX, stormZ, stormRadius, actors, engine)
                } else {
                    thinkNearBot(bot, stormX, stormZ, stormRadius, actors, engine)
                }
            }

            executeBotMotionAndCombat(bot, dt, isFar, actors, engine)
        }
    }

    private fun updateBotDrop(bot: Actor, dt: Float, engine: MatchEngine) {
        when (bot.dropPhase) {
            DropPhase.IN_PLANE -> {
                if (engine.planeProgress >= bot.planeJumpProgress) {
                    engine.ejectActorFromPlane(bot)
                    // Pick an initial POI or waypoint target
                    val poi = world.pois[rng.nextInt(world.pois.size)]
                    bot.aiWaypointX = poi.x + (rng.nextFloat() - 0.5f) * poi.radius
                    bot.aiWaypointZ = poi.z + (rng.nextFloat() - 0.5f) * poi.radius
                    bot.aiState = BotAiState.PARACHUTE
                }
            }
            DropPhase.FREEFALL, DropPhase.PARACHUTE -> {
                val dx = bot.aiWaypointX - bot.x
                val dz = bot.aiWaypointZ - bot.z
                val d = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
                val steerSpeed = if (bot.dropPhase == DropPhase.FREEFALL) 22f else 13f
                bot.vx = (dx / d) * steerSpeed
                bot.vz = (dz / d) * steerSpeed
                bot.yaw = atan2(dx, dz)
            }
            DropPhase.LANDED -> Unit
        }
    }

    private fun thinkFarBot(
        bot: Actor,
        stormX: Float, stormZ: Float, stormRadius: Float,
        actors: List<Actor>,
        engine: MatchEngine
    ) {
        val distToStormCenter = sqrt(distSq(bot.x, bot.z, stormX, stormZ))
        if (distToStormCenter > stormRadius * 0.86f) {
            bot.aiState = BotAiState.FLEE_STORM
            bot.aiWaypointX = stormX + (rng.nextFloat() - 0.5f) * stormRadius * 0.4f
            bot.aiWaypointZ = stormZ + (rng.nextFloat() - 0.5f) * stormRadius * 0.4f
            return
        }

        // Auto-equip nearby weapon if unarmed
        if (bot.activeWeapon() == null) {
            engine.tryBotQuickLoot(bot)
        }

        // Simulated bot-vs-bot encounters so the island feels alive
        var nearestEnemy: Actor? = null
        var bestDistSq = 95f * 95f
        for (j in 0 until actors.size) {
            val other = actors[j]
            if (!other.alive || other.id == bot.id || other.dropPhase != DropPhase.LANDED) continue
            val dSq = distSq(bot.x, bot.z, other.x, other.z)
            if (dSq < bestDistSq) {
                bestDistSq = dSq
                nearestEnemy = other
            }
        }
        if (nearestEnemy != null && bot.activeWeapon() != null) {
            bot.aiState = BotAiState.FIGHT
            bot.aiTargetActorId = nearestEnemy.id
            return
        }

        // Roam toward a waypoint inside the safe zone
        if (distSq(bot.x, bot.z, bot.aiWaypointX, bot.aiWaypointZ) < 16f * 16f) {
            pickSafeWaypoint(bot, stormX, stormZ, stormRadius)
        }
        bot.aiState = BotAiState.ROAM
    }

    private fun thinkNearBot(
        bot: Actor,
        stormX: Float, stormZ: Float, stormRadius: Float,
        actors: List<Actor>,
        engine: MatchEngine
    ) {
        if (bot.aiHeardTimer > 0f) bot.aiHeardTimer -= 0.05f

        // 1. Storm urgency check
        val distStorm = sqrt(distSq(bot.x, bot.z, stormX, stormZ))
        if (distStorm > stormRadius * 0.90f) {
            bot.aiState = BotAiState.FLEE_STORM
            bot.aiWaypointX = stormX + (rng.nextFloat() - 0.5f) * stormRadius * 0.35f
            bot.aiWaypointZ = stormZ + (rng.nextFloat() - 0.5f) * stormRadius * 0.35f
            bot.aiTargetActorId = -1
            return
        }

        // 2. Low HP healing / tactical retreat
        if (bot.hp < 42f && bot.bestConsumable() != null && bot.healingType == null) {
            bot.aiState = BotAiState.HEAL
            engine.startHealing(bot)
            return
        }
        if (bot.healingType != null) {
            bot.aiState = BotAiState.HEAL
            return
        }

        // 3. Find visible enemies (vision cone + LOS raycast + bush stealth!)
        var bestTarget: Actor? = null
        var bestScore = Float.MAX_VALUE
        val fwdX = sin(bot.yaw)
        val fwdZ = cos(bot.yaw)

        for (j in 0 until actors.size) {
            val other = actors[j]
            if (!other.alive || other.id == bot.id || other.dropPhase != DropPhase.LANDED) continue
            val dx = other.x - bot.x
            val dz = other.z - bot.z
            val dSq = dx * dx + dz * dz
            val maxVision = if (other.isHiddenInBush && other.isCrouching) 14f else 125f
            if (dSq > maxVision * maxVision) continue

            val dist = sqrt(dSq).coerceAtLeast(0.1f)
            val dot = (dx * fwdX + dz * fwdZ) / dist
            // Vision cone (~140 deg) or close proximity awareness
            if (dot < -0.25f && dist > 18f) continue

            // Line-of-sight raycast
            val dy = (other.y + 1.3f) - (bot.y + 1.4f)
            val rLen = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.1f)
            val ray = world.spatialGrid.raycastWorld(
                bot.x, bot.y + 1.4f, bot.z,
                dx / rLen, dy / rLen, dz / rLen,
                rLen - 0.6f, world.terrain
            )
            if (!ray.hit) {
                // Third-party bias: prefer weakened targets
                val score = dist * (0.65f + (other.hp + other.shield) / 260f)
                if (score < bestScore) {
                    bestScore = score
                    bestTarget = other
                }
            }
        }

        // 4. Loot priority if unarmed or looter personality
        if (bot.activeWeapon() == null) {
            val loot = engine.findNearestLoot(bot.x, bot.z, 70f, weaponsOnly = true)
            if (loot != null) {
                bot.aiState = BotAiState.LOOT
                bot.aiWaypointX = loot.x
                bot.aiWaypointZ = loot.z
                return
            }
        }

        // 5. Combat decision
        if (bestTarget != null && bot.activeWeapon() != null) {
            val prevTarget = bot.aiTargetActorId
            bot.aiTargetActorId = bestTarget.id
            if (prevTarget != bestTarget.id) {
                bot.aiReactionTimer = bot.aiReactionDelay
            }
            val dist = sqrt(distSq(bot.x, bot.z, bestTarget.x, bestTarget.z))
            bot.aiState = when {
                bot.hp < 32f && bot.personality != BotPersonality.AGGRESSIVE -> BotAiState.TAKE_COVER
                bestTarget.reloading && dist < 45f -> BotAiState.CHASE // Push when opponent reloads!
                bestTarget.hp < 55f && bot.personality == BotPersonality.HUNTER -> BotAiState.THIRD_PARTY
                dist > (bot.activeWeapon()?.weaponClass?.range ?: 80f) * 0.72f -> BotAiState.CHASE
                else -> BotAiState.FIGHT
            }
            return
        }

        // 6. Airdrop rush!
        val activeDrop = engine.airdrops.firstOrNull { !it.looted && distSq(bot.x, bot.z, it.x, it.z) < 240f * 240f }
        if (activeDrop != null && rng.nextFloat() < bot.personality.airdropBias) {
            bot.aiState = BotAiState.GRAB_AIRDROP
            bot.aiWaypointX = activeDrop.x
            bot.aiWaypointZ = activeDrop.z
            return
        }

        // 7. Investigate heard gunshots
        if (bot.aiHeardTimer > 0f && bot.activeWeapon() != null && bot.personality.aggression > 0.45f) {
            bot.aiState = BotAiState.INVESTIGATE_SOUND
            bot.aiWaypointX = bot.aiHeardX
            bot.aiWaypointZ = bot.aiHeardZ
            return
        }

        // 8. Bush ambush for Sneaky/Camper bots when safely inside circle
        if ((bot.personality == BotPersonality.SNEAKY || bot.personality == BotPersonality.CAMPER) &&
            bot.activeWeapon() != null && distStorm < stormRadius * 0.65f
        ) {
            val bush = world.bushes.minByOrNull { distSq(bot.x, bot.z, it.x, it.z) }
            if (bush != null && distSq(bot.x, bot.z, bush.x, bush.z) < 42f * 42f) {
                bot.aiState = BotAiState.HIDE_BUSH
                bot.aiWaypointX = bush.x
                bot.aiWaypointZ = bush.z
                return
            }
        }

        // 9. Vehicle pickup if far from safe center
        if (bot.vehicleId < 0 && distStorm > stormRadius * 0.55f) {
            val jeep = world.vehicles.firstOrNull { !it.exploded && it.driverId < 0 && distSq(bot.x, bot.z, it.x, it.z) < 26f * 26f }
            if (jeep != null) {
                bot.aiState = BotAiState.DRIVE_VEHICLE
                bot.aiWaypointX = jeep.x
                bot.aiWaypointZ = jeep.z
                return
            }
        }

        // 10. Looting nearby upgrades or roaming safe zone
        if (rng.nextFloat() < bot.personality.lootBias * 0.4f) {
            val loot = engine.findNearestLoot(bot.x, bot.z, 36f, weaponsOnly = false)
            if (loot != null) {
                bot.aiState = BotAiState.LOOT
                bot.aiWaypointX = loot.x
                bot.aiWaypointZ = loot.z
                return
            }
        }

        if (distSq(bot.x, bot.z, bot.aiWaypointX, bot.aiWaypointZ) < 12f * 12f) {
            pickSafeWaypoint(bot, stormX, stormZ, stormRadius)
        }
        bot.aiState = BotAiState.ROAM
    }

    private fun executeBotMotionAndCombat(
        bot: Actor, dt: Float, isFar: Boolean,
        actors: List<Actor>, engine: MatchEngine
    ) {
        // Stuck detection & recovery
        bot.aiStuckTimer += dt
        if (bot.aiStuckTimer >= 1.8f) {
            bot.aiStuckTimer = 0f
            val movedSq = distSq(bot.x, bot.z, bot.aiLastCheckX, bot.aiLastCheckZ)
            if (movedSq < 0.8f && bot.aiState != BotAiState.HEAL && bot.aiState != BotAiState.HIDE_BUSH) {
                // Nudge around obstacle + jump
                val escapeAngle = bot.yaw + (if (rng.nextBoolean()) 1.3f else -1.3f)
                bot.aiWaypointX = bot.x + sin(escapeAngle) * 28f
                bot.aiWaypointZ = bot.z + cos(escapeAngle) * 28f
                if (bot.grounded) bot.vy = WorldConfig.JUMP_VELOCITY
            }
            bot.aiLastCheckX = bot.x
            bot.aiLastCheckZ = bot.z
        }

        bot.isCrouching = false
        bot.isSprinting = false

        when (bot.aiState) {
            BotAiState.HEAL -> {
                bot.isCrouching = true
                bot.vx *= 0.8f; bot.vz *= 0.8f
            }
            BotAiState.HIDE_BUSH -> {
                val d = steerToward(bot, bot.aiWaypointX, bot.aiWaypointZ, 0.75f, dt)
                if (d < 1.8f) {
                    bot.isCrouching = true
                    bot.vx = 0f; bot.vz = 0f
                }
            }
            BotAiState.FIGHT, BotAiState.THIRD_PARTY, BotAiState.CHASE, BotAiState.TAKE_COVER -> {
                val target = actors.getOrNull(bot.aiTargetActorId)
                if (target == null || !target.alive || target.dropPhase != DropPhase.LANDED) {
                    bot.aiTargetActorId = -1
                    bot.aiState = BotAiState.ROAM
                    return
                }
                val dx = target.x - bot.x
                val dz = target.z - bot.z
                val dy = (target.y + 1.25f) - (bot.y + 1.45f)
                val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
                val targetYaw = atan2(dx, dz)
                val targetPitch = atan2(dy, dist).coerceIn(-0.9f, 0.9f)
                bot.yaw = lerpAngle(bot.yaw, targetYaw, (dt * 7.5f).coerceAtMost(1f))
                bot.pitch = bot.pitch + (targetPitch - bot.pitch) * (dt * 7f).coerceAtMost(1f)

                // Strafe / Chase / Retreat movement
                bot.aiStrafeTimer -= dt
                if (bot.aiStrafeTimer <= 0f) {
                    bot.aiStrafeDir = if (rng.nextBoolean()) 1f else -1f
                    bot.aiStrafeTimer = 0.7f + rng.nextFloat() * 1.1f
                }
                val moveSpeed = WorldConfig.BASE_MOVE_SPEED * 0.85f
                val fwdX = sin(bot.yaw)
                val fwdZ = cos(bot.yaw)
                val rightX = cos(bot.yaw)
                val rightZ = -sin(bot.yaw)

                when (bot.aiState) {
                    BotAiState.TAKE_COVER -> {
                        bot.vx = (-fwdX + rightX * bot.aiStrafeDir * 0.7f) * moveSpeed * 1.1f
                        bot.vz = (-fwdZ + rightZ * bot.aiStrafeDir * 0.7f) * moveSpeed * 1.1f
                    }
                    BotAiState.CHASE -> {
                        bot.isSprinting = true
                        bot.vx = (fwdX + rightX * bot.aiStrafeDir * 0.25f) * moveSpeed * 1.25f
                        bot.vz = (fwdZ + rightZ * bot.aiStrafeDir * 0.25f) * moveSpeed * 1.25f
                    }
                    else -> {
                        val idealDist = if (bot.activeWeapon()?.weaponClass?.pellets ?: 1 > 1) 14f else 32f
                        val push = if (dist > idealDist + 8f) 0.45f else if (dist < idealDist - 6f) -0.35f else 0f
                        bot.vx = (fwdX * push + rightX * bot.aiStrafeDir * 0.75f) * moveSpeed
                        bot.vz = (fwdZ * push + rightZ * bot.aiStrafeDir * 0.75f) * moveSpeed
                    }
                }

                // Reaction delay + burst fire at range
                if (bot.aiReactionTimer > 0f) {
                    bot.aiReactionTimer -= dt
                } else {
                    val w = bot.activeWeapon()
                    if (w != null) {
                        if (w.magAmmo <= 0 && !bot.reloading) {
                            engine.startReload(bot)
                        } else if (!bot.reloading && bot.fireCooldown <= 0f) {
                            if (bot.aiBurstPause > 0f) {
                                bot.aiBurstPause -= dt
                            } else {
                                if (bot.aiBurstRemaining <= 0) {
                                    bot.aiBurstRemaining = if (dist > 45f) rng.nextInt(2, 5) else rng.nextInt(4, 9)
                                }
                                // Imperfect aim error scaled by rank difficulty
                                val errDeg = (1f - bot.aiSkillAccuracy) * (if (isFar) 9.5f else 5.2f)
                                val errYaw = (rng.nextFloat() * 2f - 1f) * Math.toRadians(errDeg.toDouble()).toFloat()
                                val errPitch = (rng.nextFloat() * 2f - 1f) * Math.toRadians(errDeg.toDouble() * 0.65).toFloat()
                                engine.fireWeapon(bot, bot.yaw + errYaw, bot.pitch + errPitch)
                                bot.aiBurstRemaining--
                                if (bot.aiBurstRemaining <= 0) {
                                    bot.aiBurstPause = 0.25f + (1f - bot.aiSkillAccuracy) * 0.55f
                                }
                            }
                        }
                    }
                }
            }
            BotAiState.DRIVE_VEHICLE -> {
                val d = steerToward(bot, bot.aiWaypointX, bot.aiWaypointZ, 1.15f, dt)
                if (d < 4.5f && bot.vehicleId < 0) {
                    engine.tryToggleVehicle(bot)
                    bot.aiState = BotAiState.FLEE_STORM
                }
            }
            else -> {
                val speedMult = if (bot.aiState == BotAiState.FLEE_STORM || bot.aiState == BotAiState.GRAB_AIRDROP) 1.25f else 0.92f
                bot.isSprinting = speedMult > 1.1f
                val d = steerToward(bot, bot.aiWaypointX, bot.aiWaypointZ, speedMult, dt)
                if (d < 3.2f) {
                    engine.tryBotQuickLoot(bot)
                    if (bot.aiState == BotAiState.GRAB_AIRDROP) {
                        engine.tryOpenNearbyAirdrop(bot)
                    }
                }
            }
        }
    }

    private fun steerToward(bot: Actor, tx: Float, tz: Float, speedMult: Float, dt: Float): Float {
        val dx = tx - bot.x
        val dz = tz - bot.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 0.5f) {
            bot.vx = 0f; bot.vz = 0f
            return dist
        }
        val desiredYaw = atan2(dx, dz)
        bot.yaw = lerpAngle(bot.yaw, desiredYaw, (dt * 6f).coerceAtMost(1f))
        val spd = WorldConfig.BASE_MOVE_SPEED * speedMult
        bot.vx = sin(bot.yaw) * spd
        bot.vz = cos(bot.yaw) * spd
        return dist
    }

    private fun pickSafeWaypoint(bot: Actor, stormX: Float, stormZ: Float, stormRadius: Float) {
        val candidate = world.navWaypoints.filter {
            distSq(it[0], it[1], stormX, stormZ) < (stormRadius * 0.8f) * (stormRadius * 0.8f)
        }
        if (candidate.isNotEmpty()) {
            val wp = candidate[rng.nextInt(candidate.size)]
            bot.aiWaypointX = wp[0] + (rng.nextFloat() - 0.5f) * 24f
            bot.aiWaypointZ = wp[1] + (rng.nextFloat() - 0.5f) * 24f
        } else {
            val ang = rng.nextFloat() * 6.28f
            val r = rng.nextFloat() * (stormRadius * 0.65f).coerceAtLeast(8f)
            bot.aiWaypointX = stormX + cos(ang) * r
            bot.aiWaypointZ = stormZ + sin(ang) * r
        }
    }

    private fun distSq(x1: Float, z1: Float, x2: Float, z2: Float): Float {
        val dx = x1 - x2
        val dz = z1 - z2
        return dx * dx + dz * dz
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % (2f * PI.toFloat())
        if (diff > PI) diff -= 2f * PI.toFloat()
        if (diff < -PI) diff += 2f * PI.toFloat()
        return a + diff * t
    }
}
