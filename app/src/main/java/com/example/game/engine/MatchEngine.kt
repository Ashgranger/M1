package com.example.game.engine

import com.example.game.audio.ProceduralAudioEngine
import com.example.game.config.AmmoType
import com.example.game.config.AttachmentType
import com.example.game.config.Biome
import com.example.game.config.BotPersonality
import com.example.game.config.BotRoster
import com.example.game.config.ConsumableType
import com.example.game.config.GrenadeType
import com.example.game.config.RankTier
import com.example.game.config.Rarity
import com.example.game.config.StormConfig
import com.example.game.config.TimeOfDay
import com.example.game.config.WeaponClass
import com.example.game.config.WorldConfig
import com.example.game.entities.Actor
import com.example.game.entities.AirdropCrate
import com.example.game.entities.CombatPools
import com.example.game.entities.DropPhase
import com.example.game.entities.LootKind
import com.example.game.entities.ProjectileKind
import com.example.game.entities.WeaponInstance
import com.example.game.entities.WorldLootItem
import com.example.game.progression.MatchResultReport
import com.example.game.progression.ProgressionCatalog
import com.example.game.save.GameSettings
import com.example.game.save.PlayerSaveData
import com.example.game.world.ColliderKind
import com.example.game.world.GameWorld
import com.example.game.world.WorldAabb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ============================================================================
// SECTION: SIMULATION ENGINE — Match Flow, Movement, Weapons, Storm & Airdrops
// ============================================================================

class MatchEngine(
    private val saveData: PlayerSaveData,
    private val audio: ProceduralAudioEngine,
    private val onMatchFinished: (MatchResultReport) -> Unit
) {
    val rng = Random(System.currentTimeMillis().toInt())
    val world = GameWorld(rng.nextInt(1, 99999))
    val pools = CombatPools()
    val input = InputState()
    val timeOfDay: TimeOfDay = when (rng.nextInt(100)) {
        in 0..54 -> TimeOfDay.DAY
        in 55..84 -> TimeOfDay.SUNSET
        else -> TimeOfDay.NIGHT
    }

    @Volatile var settings: GameSettings = saveData.settings
    @Volatile var paused: Boolean = false
    @Volatile var waypointX: Float? = null
    @Volatile var waypointZ: Float? = null

    val actors = ArrayList<Actor>(WorldConfig.TOTAL_PLAYERS)
    val airdrops = ArrayList<AirdropCrate>(4)
    val human: Actor

    // Match flow & timers
    var phase: MatchPhase = MatchPhase.MATCHMAKING
    private var matchmakingTimer = 0f
    private var matchTimeSec = 0f
    private var survivalTimeSec = 0f
    var planeProgress = 0f
    val planeStartX: Float
    val planeStartZ: Float
    val planeEndX: Float
    val planeEndZ: Float
    var planeX = 0f
    var planeZ = 0f
    val planeYaw: Float

    // Storm state (7 stages)
    var stormStageIdx = 0
    var stormShrinking = false
    var stormStageTimer = StormConfig.STAGES[0].waitSeconds
    var stormCenterX = 0f
    var stormCenterZ = 0f
    var stormRadius = 990f
    var startStormX = 0f
    var startStormZ = 0f
    var startStormRadius = 990f
    var nextStormX = (rng.nextFloat() - 0.5f) * 320f
    var nextStormZ = (rng.nextFloat() - 0.5f) * 320f
    var nextStormRadius = StormConfig.STAGES[0].targetRadius
    private var nextAirdropTime = 65f

    // Camera & juice state (read by OpenGL renderer)
    var cameraShake = 0f
    var recoilPitchOffset = 0f
    var hitMarkerAlpha = 0f
    var hitMarkerHeadshot = false
    var slowMoTimer = 0f
    var spectateIndex = 0
    private var resultEmitted = false

    // Tracking for personal bests & progression
    private var airdropsLootedByHuman = 0
    private var cratesBrokenByHuman = 0
    private var vehicleDriveTimeSec = 0f
    private var humanDistanceTraveled = 0f
    private val humanWeaponDamage = HashMap<String, Int>()

    // Kill feed & banners
    private var nextFeedId = 1L
    private val killFeed = ArrayDeque<KillFeedEntry>(8)
    private val damageIndicators = ArrayList<MutableDamageIndicator>(6)
    private val recentShotBlips = ArrayList<ShotBlip>(12)
    private var killStreakText: String? = null
    private var killStreakTimer = 0f
    private var airdropBannerText: String? = null
    private var airdropBannerTimer = 0f

    // Scratch buffers
    private val lookScratch = FloatArray(2)
    private val collisionScratch = ArrayList<WorldAabb>(32)
    private val botAi = BotAiController(world, rng)
    private var hudAccum = 0f
    private var fpsFrames = 0
    private var fpsAccum = 0f
    @Volatile var currentFps = 60

    @Volatile var hudSnapshot: MatchHudState = MatchHudState(timeOfDay = timeOfDay)

    private data class MutableDamageIndicator(var srcX: Float, var srcZ: Float, var alpha: Float)
    private data class ShotBlip(var x: Float, var z: Float, var ttl: Float)

    init {
        // Random flight path across island
        val angle = rng.nextFloat() * 2f * PI.toFloat()
        planeStartX = cos(angle) * -940f
        planeStartZ = sin(angle) * -940f
        planeEndX = cos(angle) * 940f
        planeEndZ = sin(angle) * 940f
        planeX = planeStartX
        planeZ = planeStartZ
        planeYaw = atan2(planeEndX - planeStartX, planeEndZ - planeStartZ)

        // Build human actor with equipped cosmetics
        val skin = ProgressionCatalog.COSMETIC_BY_ID[saveData.equippedCharacterSkin]
        val chute = ProgressionCatalog.COSMETIC_BY_ID[saveData.equippedParachute]
        val trail = ProgressionCatalog.COSMETIC_BY_ID[saveData.equippedBulletTrail]
        human = Actor(
            id = 0,
            name = saveData.playerName.ifBlank { "OPERATIVE" },
            isHuman = true,
            personality = BotPersonality.AGGRESSIVE,
            skinPrimaryRgb = skin?.primaryRgb ?: floatArrayOf(0.16f, 0.24f, 0.36f),
            skinAccentRgb = skin?.accentRgb ?: floatArrayOf(0.0f, 0.94f, 1.0f),
            parachuteRgb = chute?.primaryRgb ?: floatArrayOf(0.0f, 0.94f, 1.0f),
            trailRgb = trail?.primaryRgb ?: floatArrayOf(0.0f, 0.94f, 1.0f)
        ).apply {
            flashlightOn = timeOfDay.isNight
            // Give starter Pistol + bandages so early combat feels responsive immediately on landing
            weaponSlots[0] = WeaponInstance(WeaponClass.PISTOL, Rarity.COMMON)
            ammoReserves[AmmoType.LIGHT.ordinal] = 30
            consumables[ConsumableType.BANDAGE.ordinal] = 2
            grenades[GrenadeType.FRAG.ordinal] = 1
        }
        actors.add(human)

        // Build 29 bots scaled by player rank (mixed lobby)
        val (playerRank, _) = RankTier.fromRp(saveData.rp)
        val botNames = BotRoster.NAMES.shuffled(rng)
        val botSkins = ProgressionCatalog.COSMETICS.filter { it.category.name == "CHARACTER_SKIN" }
        for (b in 1..WorldConfig.BOT_COUNT) {
            val botSkin = botSkins[rng.nextInt(botSkins.size)]
            val pers = BotPersonality.entries[rng.nextInt(BotPersonality.entries.size)]
            val bot = Actor(
                id = b,
                name = botNames[(b - 1) % botNames.size],
                isHuman = false,
                personality = pers,
                skinPrimaryRgb = botSkin.primaryRgb,
                skinAccentRgb = botSkin.accentRgb,
                parachuteRgb = botSkin.accentRgb,
                trailRgb = floatArrayOf(1.0f, 0.55f, 0.25f)
            ).apply {
                planeJumpProgress = 0.14f + rng.nextFloat() * 0.72f
                flashlightOn = timeOfDay.isNight
                // Mixed lobby skill variance around player's rank tier
                val variance = (rng.nextFloat() - 0.5f) * 0.24f
                aiSkillAccuracy = (playerRank.botAccuracyScale + variance).coerceIn(0.28f, 0.96f)
                aiReactionDelay = (playerRank.botReactionDelay + (rng.nextFloat() - 0.5f) * 0.18f).coerceIn(0.12f, 0.85f)
                weaponSlots[0] = WeaponInstance(WeaponClass.PISTOL, Rarity.COMMON)
                ammoReserves[AmmoType.LIGHT.ordinal] = 40
                consumables[ConsumableType.BANDAGE.ordinal] = 2
            }
            actors.add(bot)
        }

        audio.isInMatch = true
        audio.stormStage = 1
        publishHudSnapshot(emptyList())
    }

    fun setWaypoint(wx: Float, wz: Float) {
        waypointX = wx.coerceIn(-WorldConfig.HALF_WORLD, WorldConfig.HALF_WORLD)
        waypointZ = wz.coerceIn(-WorldConfig.HALF_WORLD, WorldConfig.HALF_WORLD)
        audio.playUiClick()
    }

    fun clearWaypoint() {
        waypointX = null
        waypointZ = null
        audio.playUiClick()
    }

    fun getObservedActor(): Actor {
        if (human.alive) return human
        val spec = actors.getOrNull(spectateIndex)
        if (spec != null && spec.alive) return spec
        val fallback = actors.firstOrNull { it.alive }
        if (fallback != null) spectateIndex = fallback.id
        return fallback ?: human
    }

    fun step(rawDt: Float, projectedDamageNumbers: List<ScreenDamageNumber>) {
        if (paused) {
            audio.isPaused = true
            return
        }
        audio.isPaused = false

        val clampedDt = rawDt.coerceIn(0.001f, WorldConfig.MAX_DELTA_TIME)
        fpsFrames++
        fpsAccum += clampedDt
        if (fpsAccum >= 0.5f) {
            currentFps = (fpsFrames / fpsAccum).toInt().coerceIn(1, 120)
            fpsFrames = 0
            fpsAccum = 0f
        }

        // Dramatic slow-motion on final kill!
        val timeScale = if (slowMoTimer > 0f) {
            slowMoTimer = (slowMoTimer - clampedDt).coerceAtLeast(0f)
            0.25f
        } else 1f
        val dt = clampedDt * timeScale

        // Phase 1: "Finding players 14/30" quick lobby animation (2.4 seconds)
        if (phase == MatchPhase.MATCHMAKING) {
            matchmakingTimer += clampedDt
            val found = (1 + ((matchmakingTimer / 2.2f) * 29f).toInt()).coerceIn(1, 30)
            if (matchmakingTimer >= 2.35f) {
                phase = MatchPhase.IN_PLANE
            }
            hudSnapshot = hudSnapshot.copy(phase = phase, matchmakingFound = found, fps = currentFps)
            return
        }

        matchTimeSec += dt
        if (human.alive) survivalTimeSec += dt

        // Update plane flight
        if (planeProgress < 1.15f) {
            planeProgress += dt / 18f
            planeX = planeStartX + (planeEndX - planeStartX) * planeProgress.coerceIn(0f, 1f)
            planeZ = planeStartZ + (planeEndZ - planeStartZ) * planeProgress.coerceIn(0f, 1f)
            for (i in 0 until actors.size) {
                val a = actors[i]
                if (a.alive && a.dropPhase == DropPhase.IN_PLANE) {
                    a.x = planeX
                    a.y = WorldConfig.PLANE_ALTITUDE
                    a.z = planeZ
                    a.yaw = planeYaw
                }
            }
            // Auto-eject remaining players near end of island path
            if (planeProgress >= 0.88f) {
                for (i in 0 until actors.size) {
                    val a = actors[i]
                    if (a.alive && a.dropPhase == DropPhase.IN_PLANE) ejectActorFromPlane(a)
                }
            }
        }

        // Update human input & spectate switching
        if (phase == MatchPhase.IN_PLANE || phase == MatchPhase.ACTIVE) {
            updateHumanInput(dt)
        } else if (phase == MatchPhase.SPECTATING) {
            if (input.spectateNextPressed.getAndSet(false)) {
                cycleSpectateTarget()
            }
        }

        // Update 29-bot AI
        botAi.updateBots(dt, actors, human, stormCenterX, stormCenterZ, stormRadius, this)

        // Physics & state updates for all actors
        for (i in 0 until actors.size) {
            updateActorPhysics(actors[i], dt)
        }

        // Update Vehicles
        updateVehicles(dt)

        // Update 7-stage Storm & mid-match Airdrops
        updateStormAndAirdrops(dt)

        // Update Projectiles, Tracers, Particles, Smoke Clouds & Damage Numbers
        updatePools(dt)

        // Decay feedback effects
        cameraShake = (cameraShake - clampedDt * 2.8f).coerceAtLeast(0f)
        recoilPitchOffset *= (1f - (clampedDt * 12f).coerceAtMost(0.95f))
        hitMarkerAlpha = (hitMarkerAlpha - clampedDt * 4.5f).coerceAtLeast(0f)
        if (killStreakTimer > 0f) {
            killStreakTimer -= clampedDt
            if (killStreakTimer <= 0f) killStreakText = null
        }
        if (airdropBannerTimer > 0f) {
            airdropBannerTimer -= clampedDt
            if (airdropBannerTimer <= 0f) airdropBannerText = null
        }
        var dIdx = damageIndicators.size - 1
        while (dIdx >= 0) {
            damageIndicators[dIdx].alpha -= clampedDt * 0.95f
            if (damageIndicators[dIdx].alpha <= 0f) damageIndicators.removeAt(dIdx)
            dIdx--
        }
        var sIdx = recentShotBlips.size - 1
        while (sIdx >= 0) {
            recentShotBlips[sIdx].ttl -= clampedDt
            if (recentShotBlips[sIdx].ttl <= 0f) recentShotBlips.removeAt(sIdx)
            sIdx--
        }

        // Sync ambient audio state
        val obs = getObservedActor()
        audio.inPlane = obs.dropPhase == DropPhase.IN_PLANE
        audio.inParachute = obs.dropPhase == DropPhase.FREEFALL || obs.dropPhase == DropPhase.PARACHUTE
        audio.inStorm = isInStorm(obs.x, obs.z)
        audio.stormStage = stormStageIdx + 1

        // Check Victory condition (1 alive)
        val aliveCount = actors.count { it.alive }
        if (aliveCount <= 1 && !resultEmitted) {
            if (human.alive) {
                human.placement = 1
                finishMatch()
            } else if (phase == MatchPhase.SPECTATING && slowMoTimer <= 0f) {
                finishMatch()
            }
        }

        // Auto-clear waypoint pin upon arrival within 18m
        val wx = waypointX
        val wz = waypointZ
        if (wx != null && wz != null && human.alive && human.dropPhase == DropPhase.LANDED) {
            val dx = human.x - wx
            val dz = human.z - wz
            if (dx * dx + dz * dz < 18f * 18f) {
                waypointX = null
                waypointZ = null
            }
        }

        // Throttled HUD snapshot (~30Hz)
        hudAccum += clampedDt
        if (hudAccum >= 0.033f) {
            hudAccum = 0f
            publishHudSnapshot(projectedDamageNumbers)
        }
    }

    fun ejectActorFromPlane(actor: Actor) {
        if (actor.dropPhase != DropPhase.IN_PLANE) return
        actor.dropPhase = DropPhase.FREEFALL
        actor.y = WorldConfig.PLANE_ALTITUDE - 3f
        actor.vy = -12f
        if (actor.isHuman) {
            phase = MatchPhase.ACTIVE
            audio.playUiClick()
        }
    }

    private fun updateHumanInput(dt: Float) {
        if (!human.alive) return

        // Look rotation (touch + gyro + mouse)
        input.consumeLookDelta(lookScratch)
        val sens = settings.lookSensitivity * (if (human.isAiming && human.activeWeapon()?.hasScope == true) 0.42f else 1f)
        val inv = if (settings.invertY) -1f else 1f
        human.yaw += lookScratch[0] * sens
        human.pitch = (human.pitch + lookScratch[1] * sens * inv).coerceIn(-1.15f, 1.15f)

        // Optional Aim Assist: gently magnetize crosshair toward visible nearby enemy when aiming/firing
        if (settings.aimAssistEnabled && (input.aimHeld || input.fireHeld) && human.dropPhase == DropPhase.LANDED) {
            applyAimAssist(dt)
        }

        // Plane jump trigger
        if (human.dropPhase == DropPhase.IN_PLANE) {
            if (input.planeJumpPressed.getAndSet(false) || input.jumpPressed.getAndSet(false)) {
                ejectActorFromPlane(human)
            }
            return
        }

        // Flashlight toggle
        if (input.flashlightPressed.getAndSet(false)) {
            human.flashlightOn = !human.flashlightOn
            audio.playUiClick()
        }

        // Weapon slot switching (1..5)
        val reqSlot = input.selectSlot.getAndSet(-1)
        if (reqSlot in 0..4 && reqSlot != human.activeSlot) {
            human.activeSlot = reqSlot
            human.reloading = false
            human.cancelHeal()
            audio.playUiClick()
        }

        // Drop active weapon (Q)
        if (input.dropPressed.getAndSet(false)) {
            val w = human.activeWeapon()
            if (w != null && human.dropPhase == DropPhase.LANDED) {
                world.lootItems.add(
                    WorldLootItem(
                        id = world.lootItems.size + 1000,
                        x = human.x + sin(human.yaw) * 1.5f,
                        y = human.y + 0.3f,
                        z = human.z + cos(human.yaw) * 1.5f,
                        kind = LootKind.WEAPON,
                        rarity = w.rarity,
                        weaponClass = w.weaponClass
                    )
                )
                human.weaponSlots[human.activeSlot] = null
                audio.playUiClick()
            }
        }

        // Movement inputs
        val mx = input.effectiveMoveX()
        val my = input.effectiveMoveY()
        val fwdX = sin(human.yaw)
        val fwdZ = cos(human.yaw)
        val rightX = cos(human.yaw)
        val rightZ = -sin(human.yaw)

        // Freefall & Parachute steering
        if (human.dropPhase == DropPhase.FREEFALL || human.dropPhase == DropPhase.PARACHUTE) {
            val steerSpd = if (human.dropPhase == DropPhase.FREEFALL) 24f else 15f
            human.vx = (fwdX * my + rightX * mx) * steerSpd
            human.vz = (fwdZ * my + rightZ * mx) * steerSpd
            return
        }

        // Vehicle Driving
        if (human.vehicleId >= 0) {
            val veh = world.vehicles.getOrNull(human.vehicleId)
            if (veh != null && !veh.exploded) {
                vehicleDriveTimeSec += dt
                val accel = 26f
                if (abs(my) > 0.05f) {
                    veh.speed = (veh.speed + my * accel * dt).coerceIn(-14f, 34f)
                } else {
                    veh.speed *= (1f - (dt * 3.5f).coerceAtMost(0.9f))
                }
                if (abs(veh.speed) > 0.6f) {
                    val turnSign = if (veh.speed >= 0f) 1f else -1f
                    veh.yaw += mx * 2.1f * turnSign * dt
                }
                if (input.vehicleOrMeleePressed.getAndSet(false) || input.jumpPressed.getAndSet(false)) {
                    exitVehicle(human)
                }
                return
            } else {
                human.vehicleId = -1
            }
        }

        // Aiming / ADS
        human.isAiming = input.aimHeld && human.healingType == null

        // Crouch & Sprint-Slide
        if (input.crouchPressed.getAndSet(false)) {
            if (human.isSprinting && human.grounded && !human.isSliding && human.stamina > 15f) {
                human.isSliding = true
                human.slideTimer = 0.62f
                human.isCrouching = true
                human.stamina = (human.stamina - 15f).coerceAtLeast(0f)
            } else {
                human.isCrouching = !human.isCrouching
            }
        }

        // Sprint & stamina
        val wantsSprint = (input.sprintHeld || input.sprintLocked) && my > 0.35f && !human.isCrouching && !human.isAiming && !human.isSwimming
        if (wantsSprint && human.stamina > 2f) {
            human.isSprinting = true
            human.stamina = (human.stamina - 18f * dt).coerceAtLeast(0f)
        } else {
            human.isSprinting = false
            human.stamina = (human.stamina + 22f * dt).coerceAtMost(WorldConfig.MAX_STAMINA)
        }

        // Ground movement vector
        val biome = world.terrain.getBiome(human.x, human.z)
        var speed = WorldConfig.BASE_MOVE_SPEED * biome.speedMod
        if (human.speedBoostTimer > 0f) speed *= 1.24f
        if (human.isSliding) {
            speed = WorldConfig.SLIDE_SPEED
            human.slideTimer -= dt
            if (human.slideTimer <= 0f) human.isSliding = false
        } else if (human.isSwimming) {
            speed *= WorldConfig.SWIM_MULTIPLIER
        } else if (human.isSprinting) {
            speed *= WorldConfig.SPRINT_MULTIPLIER
        } else if (human.isCrouching || human.isAiming || human.healingType != null) {
            speed *= WorldConfig.CROUCH_MULTIPLIER
        }

        val dirX = if (human.isSliding) fwdX else (fwdX * my + rightX * mx)
        val dirZ = if (human.isSliding) fwdZ else (fwdZ * my + rightZ * mx)
        val len = sqrt(dirX * dirX + dirZ * dirZ)
        if (len > 0.01f) {
            val norm = min(1f, len)
            human.vx = (dirX / len) * speed * norm
            human.vz = (dirZ / len) * speed * norm
        } else {
            human.vx *= (1f - (dt * 12f).coerceAtMost(0.95f))
            human.vz *= (1f - (dt * 12f).coerceAtMost(0.95f))
        }

        // Jump / Vault
        if (input.jumpPressed.getAndSet(false)) {
            if (human.grounded) {
                human.vy = WorldConfig.JUMP_VELOCITY
                human.grounded = false
                human.isCrouching = false
                human.isSliding = false
            }
        }

        // Pickup loot or open airdrop (E)
        if (input.pickupPressed.getAndSet(false)) {
            if (!tryOpenNearbyAirdrop(human)) {
                tryPickupNearestLoot(human)
            }
        }

        // Vehicle enter or Melee attack (F)
        if (input.vehicleOrMeleePressed.getAndSet(false)) {
            if (!tryToggleVehicle(human)) {
                performMelee(human)
            }
        }

        // Reload (R)
        if (input.reloadPressed.getAndSet(false)) {
            startReload(human)
        }

        // Healing consumable
        if (input.healPressed.getAndSet(false)) {
            startHealing(human)
        }

        // Cycle or Throw Grenade (G)
        if (input.cycleGrenadePressed.getAndSet(false)) {
            val types = GrenadeType.entries
            human.selectedGrenade = types[(human.selectedGrenade.ordinal + 1) % types.size]
            audio.playUiClick()
        }
        if (input.grenadePressed.getAndSet(false)) {
            throwGrenade(human)
        }

        // Fire weapon
        if (input.fireHeld) {
            val w = human.activeWeapon()
            if (w != null) {
                if (w.magAmmo <= 0 && !human.reloading) {
                    startReload(human)
                } else if (!human.reloading && human.fireCooldown <= 0f) {
                    fireWeapon(human, human.yaw, human.pitch)
                    if (!w.weaponClass.autoFire) {
                        input.fireHeld = false
                    }
                }
            }
        }
    }

    private fun applyAimAssist(dt: Float) {
        val fwdX = sin(human.yaw) * cos(human.pitch)
        val fwdY = sin(human.pitch)
        val fwdZ = cos(human.yaw) * cos(human.pitch)
        var bestActor: Actor? = null
        var bestDot = 0.965f // ~15 deg cone
        for (i in 1 until actors.size) {
            val b = actors[i]
            if (!b.alive || b.dropPhase != DropPhase.LANDED) continue
            val dx = b.x - human.x
            val dy = (b.y + 1.25f) - (human.y + 1.55f)
            val dz = b.z - human.z
            val d = sqrt(dx * dx + dy * dy + dz * dz)
            if (d > 140f || d < 1f) continue
            val dot = (dx * fwdX + dy * fwdY + dz * fwdZ) / d
            if (dot > bestDot) {
                bestDot = dot
                bestActor = b
            }
        }
        if (bestActor != null) {
            val dx = bestActor.x - human.x
            val dz = bestActor.z - human.z
            val dy = (bestActor.y + 1.2f) - (human.y + 1.55f)
            val targetYaw = atan2(dx, dz)
            val targetPitch = atan2(dy, sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f))
            var diffY = (targetYaw - human.yaw) % (2f * PI.toFloat())
            if (diffY > PI) diffY -= 2f * PI.toFloat()
            if (diffY < -PI) diffY += 2f * PI.toFloat()
            human.yaw += diffY * (dt * 6.5f).coerceAtMost(0.35f)
            human.pitch += (targetPitch - human.pitch) * (dt * 5.5f).coerceAtMost(0.30f)
        }
    }

    private fun updateActorPhysics(a: Actor, dt: Float) {
        if (!a.alive) {
            a.deathTimer += dt
            return
        }

        if (a.fireCooldown > 0f) a.fireCooldown = (a.fireCooldown - dt).coerceAtLeast(0f)
        if (a.shootAnimTimer > 0f) a.shootAnimTimer = (a.shootAnimTimer - dt).coerceAtLeast(0f)
        if (a.speedBoostTimer > 0f) a.speedBoostTimer = (a.speedBoostTimer - dt).coerceAtLeast(0f)
        if (a.flashBlindTimer > 0f) a.flashBlindTimer = (a.flashBlindTimer - dt).coerceAtLeast(0f)

        // Reload timer
        if (a.reloading) {
            a.reloadTimer -= dt
            if (a.reloadTimer <= 0f) {
                a.reloading = false
                val w = a.activeWeapon()
                if (w != null) {
                    val need = w.effectiveMagSize - w.magAmmo
                    val idx = w.weaponClass.ammoType.ordinal
                    val avail = a.ammoReserves[idx]
                    val take = min(need, avail)
                    w.magAmmo += take
                    a.ammoReserves[idx] = avail - take
                }
            }
        }

        // Healing channel
        val hType = a.healingType
        if (hType != null) {
            a.healingTimer += dt
            if (a.healingTimer >= a.healingDuration) {
                if (hType.hpGain > 0f) a.hp = min(hType.maxCap, a.hp + hType.hpGain).coerceAtLeast(a.hp)
                if (hType.shieldGain > 0f) a.shield = min(hType.maxCap, a.shield + hType.shieldGain).coerceAtLeast(a.shield)
                if (hType.speedBoostSeconds > 0f) a.speedBoostTimer = hType.speedBoostSeconds
                a.consumables[hType.ordinal] = (a.consumables[hType.ordinal] - 1).coerceAtLeast(0)
                a.cancelHeal()
                if (a.isHuman) audio.playPickup()
            }
        }

        if (a.dropPhase == DropPhase.IN_PLANE || a.vehicleId >= 0) return

        // Freefall & Parachute descent
        if (a.dropPhase == DropPhase.FREEFALL || a.dropPhase == DropPhase.PARACHUTE) {
            val groundY = world.terrain.getHeight(a.x, a.z)
            val alt = a.y - groundY
            if (a.dropPhase == DropPhase.FREEFALL && alt <= WorldConfig.PARACHUTE_OPEN_ALTITUDE) {
                a.dropPhase = DropPhase.PARACHUTE
                if (a.isHuman) audio.playPickup()
            }
            a.vy = if (a.dropPhase == DropPhase.FREEFALL) -34f else -11.5f
            a.x = (a.x + a.vx * dt).coerceIn(-WorldConfig.HALF_WORLD + 20f, WorldConfig.HALF_WORLD - 20f)
            a.z = (a.z + a.vz * dt).coerceIn(-WorldConfig.HALF_WORLD + 20f, WorldConfig.HALF_WORLD - 20f)
            a.y += a.vy * dt
            if (a.y <= groundY + 0.1f) {
                a.y = groundY
                a.vy = 0f
                a.grounded = true
                a.dropPhase = DropPhase.LANDED
                spawnLandingEffect(a)
            }
            return
        }

        // Gravity & capsule collision
        val prevX = a.x
        val prevZ = a.z
        a.vy -= WorldConfig.GRAVITY * dt
        val nextX = a.x + a.vx * dt
        val nextY = a.y + a.vy * dt
        val nextZ = a.z + a.vz * dt
        val height = if (a.isCrouching) WorldConfig.PLAYER_CROUCH_HEIGHT else WorldConfig.PLAYER_HEIGHT
        val res = world.spatialGrid.resolveCapsule(nextX, nextY, nextZ, WorldConfig.PLAYER_RADIUS, height, world.terrain, collisionScratch)

        a.x = res.x
        a.y = res.y
        a.z = res.z
        a.grounded = res.grounded
        if (a.grounded && a.vy < 0f) a.vy = 0f

        // Vaulting over low walls / window sills / crates
        if (res.canVault && a.vy > 2f) {
            a.y = res.vaultTargetY
            a.vy = 3.5f
            a.isVaulting = true
            a.vaultTimer = 0.28f
        }
        if (a.vaultTimer > 0f) {
            a.vaultTimer -= dt
            if (a.vaultTimer <= 0f) a.isVaulting = false
        }

        // Water swimming
        if (a.y < WorldConfig.SWIM_DEPTH) {
            a.y = WorldConfig.SWIM_DEPTH
            a.vy = 0f
            a.isSwimming = true
            a.grounded = true
        } else {
            a.isSwimming = false
        }

        // Bush concealment
        a.isHiddenInBush = world.isInsideBush(a.x, a.z)

        // Animation walk cycle & surface footsteps
        val moveStep = sqrt((a.x - prevX) * (a.x - prevX) + (a.z - prevZ) * (a.z - prevZ))
        if (a.isHuman) humanDistanceTraveled += moveStep
        if (moveStep > 0.005f) {
            a.animTime += dt * (if (a.isSprinting) 11f else 7.5f)
            a.footstepAccum += moveStep
            if (a.isHuman && a.grounded && a.footstepAccum >= 2.3f) {
                a.footstepAccum = 0f
                audio.playFootstep(world.terrain.getBiome(a.x, a.z), res.indoor)
            }
        }
    }

    private fun updateVehicles(dt: Float) {
        for (i in 0 until world.vehicles.size) {
            val v = world.vehicles[i]
            if (v.exploded) continue
            if (abs(v.speed) > 0.05f) {
                val nx = (v.x + sin(v.yaw) * v.speed * dt).coerceIn(-WorldConfig.HALF_WORLD + 12f, WorldConfig.HALF_WORLD - 12f)
                val nz = (v.z + cos(v.yaw) * v.speed * dt).coerceIn(-WorldConfig.HALF_WORLD + 12f, WorldConfig.HALF_WORLD - 12f)
                val ny = world.terrain.getHeight(nx, nz)
                if (ny > WorldConfig.WATER_LEVEL - 0.5f) {
                    v.x = nx; v.z = nz; v.y = ny
                } else {
                    v.speed *= -0.3f
                }
                if (v.driverId < 0) v.speed *= (1f - (dt * 4f).coerceAtMost(0.95f))

                // Roadkill check if moving fast
                if (abs(v.speed) > 12f && v.driverId >= 0) {
                    val driver = actors.getOrNull(v.driverId)
                    for (j in 0 until actors.size) {
                        val target = actors[j]
                        if (!target.alive || target.id == v.driverId || target.dropPhase != DropPhase.LANDED) continue
                        val dSq = (target.x - v.x) * (target.x - v.x) + (target.z - v.z) * (target.z - v.z)
                        if (dSq < 2.6f * 2.6f) {
                            applyDamage(target, driver, 55f, false, "Vehicle", v.x, v.z)
                            v.speed *= 0.65f
                        }
                    }
                }
            }
            // Keep driver locked inside jeep
            if (v.driverId >= 0) {
                val d = actors.getOrNull(v.driverId)
                if (d != null && d.alive) {
                    d.x = v.x; d.y = v.y + 0.9f; d.z = v.z
                    if (!d.isHuman) {
                        // Bot drives jeep toward waypoint
                        val dx = d.aiWaypointX - v.x
                        val dz = d.aiWaypointZ - v.z
                        val dist = sqrt(dx * dx + dz * dz)
                        if (dist > 12f) {
                            v.yaw = atan2(dx, dz)
                            v.speed = 22f
                        } else {
                            exitVehicle(d)
                        }
                    }
                } else {
                    v.driverId = -1
                }
            }
        }
    }

    private fun updateStormAndAirdrops(dt: Float) {
        val stage = StormConfig.STAGES[stormStageIdx]
        stormStageTimer -= dt
        if (!stormShrinking) {
            if (stormStageTimer <= 0f) {
                stormShrinking = true
                stormStageTimer = stage.shrinkSeconds
                startStormX = stormCenterX
                startStormZ = stormCenterZ
                startStormRadius = stormRadius
            }
        } else {
            val t = (1f - (stormStageTimer / stage.shrinkSeconds)).coerceIn(0f, 1f)
            stormCenterX = startStormX + (nextStormX - startStormX) * t
            stormCenterZ = startStormZ + (nextStormZ - startStormZ) * t
            stormRadius = startStormRadius + (nextStormRadius - startStormRadius) * t
            if (stormStageTimer <= 0f) {
                stormShrinking = false
                if (stormStageIdx < StormConfig.STAGES.lastIndex) {
                    stormStageIdx++
                    val nextStage = StormConfig.STAGES[stormStageIdx]
                    stormStageTimer = nextStage.waitSeconds
                    val maxShift = (stormRadius - nextStage.targetRadius).coerceAtLeast(0f) * 0.65f
                    val ang = rng.nextFloat() * 6.28f
                    nextStormX = (stormCenterX + cos(ang) * maxShift * rng.nextFloat()).coerceIn(-680f, 680f)
                    nextStormZ = (stormCenterZ + sin(ang) * maxShift * rng.nextFloat()).coerceIn(-680f, 680f)
                    nextStormRadius = nextStage.targetRadius
                } else {
                    stormStageTimer = 999f
                }
            }
        }

        // Apply storm DPS to everyone outside the safe circle
        val dps = StormConfig.STAGES[stormStageIdx].damagePerSecond
        for (i in 0 until actors.size) {
            val a = actors[i]
            if (!a.alive || a.dropPhase != DropPhase.LANDED) continue
            if (isInStorm(a.x, a.z)) {
                a.hp -= dps * dt
                if (a.hp <= 0f) {
                    killActor(a, null, "The Storm", false)
                }
            }
        }

        // Mid-match Airdrops
        if (matchTimeSec >= nextAirdropTime && airdrops.size < 3 && stormRadius > 60f) {
            nextAirdropTime += 85f
            val ang = rng.nextFloat() * 6.28f
            val r = rng.nextFloat() * stormRadius * 0.65f
            val ax = stormCenterX + cos(ang) * r
            val az = stormCenterZ + sin(ang) * r
            val gy = max(1f, world.terrain.getHeight(ax, az))
            airdrops.add(AirdropCrate(airdrops.size + 1, ax, gy + 145f, az, gy))
            airdropBannerText = "SUPPLY AIRDROP INCOMING — LEGENDARY LOOT"
            airdropBannerTimer = 4.2f
            audio.playAirdropSiren()
        }

        // Animate descending airdrops & rising smoke beacon
        for (i in 0 until airdrops.size) {
            val drop = airdrops[i]
            if (!drop.landed) {
                drop.y -= 18f * dt
                if (drop.y <= drop.groundY) {
                    drop.y = drop.groundY
                    drop.landed = true
                }
            } else if (!drop.looted && drop.smokeTimer > 0f) {
                drop.smokeTimer -= dt
                if (rng.nextFloat() < dt * 8f) {
                    pools.spawnParticle(
                        drop.x + (rng.nextFloat() - 0.5f), drop.y + 1.5f, drop.z + (rng.nextFloat() - 0.5f),
                        (rng.nextFloat() - 0.5f) * 1.2f, 5.5f + rng.nextFloat() * 2f, (rng.nextFloat() - 0.5f) * 1.2f,
                        1.0f, 0.18f, 0.35f, 0.85f, 2.2f, gravity = -0.8f, isSmoke = true
                    )
                }
            }
        }
    }

    fun isInStorm(x: Float, z: Float): Boolean {
        val dx = x - stormCenterX
        val dz = z - stormCenterZ
        return dx * dx + dz * dz > stormRadius * stormRadius
    }

    fun fireWeapon(shooter: Actor, aimYaw: Float, aimPitch: Float) {
        val w = shooter.activeWeapon() ?: return
        if (w.magAmmo <= 0 || shooter.reloading) return

        shooter.cancelHeal()
        w.magAmmo--
        shooter.fireCooldown = 1f / w.weaponClass.fireRate
        shooter.shootAnimTimer = 0.14f
        shooter.shotsFired++

        val eyeX = shooter.x + sin(aimYaw) * 0.55f
        val eyeY = shooter.y + (if (shooter.isCrouching) 1.05f else 1.52f)
        val eyeZ = shooter.z + cos(aimYaw) * 0.55f

        val obs = getObservedActor()
        audio.playWeaponShot(w.weaponClass, w.isSilenced, obs.x, obs.z, obs.yaw, shooter.x, shooter.z)
        if (!w.isSilenced) {
            recentShotBlips.add(ShotBlip(shooter.x, shooter.z, 2.4f))
        }
        botAi.notifyGunshot(shooter.id, shooter.x, shooter.z, if (w.isSilenced) w.weaponClass.soundRadius * 0.4f else w.weaponClass.soundRadius, actors)

        // Muzzle flash & shell casing particles
        if (!w.isSilenced) {
            pools.spawnParticle(eyeX + sin(aimYaw) * 0.6f, eyeY - 0.08f, eyeZ + cos(aimYaw) * 0.6f, 0f, 0.5f, 0f, 1.0f, 0.85f, 0.35f, 0.42f, 0.07f, 0f)
        }
        // Shell casing ejection
        val rightX = cos(aimYaw)
        val rightZ = -sin(aimYaw)
        pools.spawnParticle(
            eyeX + rightX * 0.25f, eyeY - 0.12f, eyeZ + rightZ * 0.25f,
            rightX * (2.2f + rng.nextFloat()), 2.4f + rng.nextFloat(), rightZ * (2.2f + rng.nextFloat()),
            0.92f, 0.76f, 0.25f, 0.10f, 0.55f, 16f
        )

        if (shooter.isHuman) {
            cameraShake = (cameraShake + w.effectiveRecoil * 0.12f).coerceAtMost(0.85f)
            recoilPitchOffset = (recoilPitchOffset + w.effectiveRecoil * 0.015f).coerceAtMost(0.14f)
            shooter.pitch = (shooter.pitch + w.effectiveRecoil * 0.008f).coerceAtMost(1.15f)
        }

        // Physical projectile weapons (Rocket Launcher & Crossbow)
        if (!w.weaponClass.isHitscan) {
            val dx = sin(aimYaw) * cos(aimPitch)
            val dy = sin(aimPitch)
            val dz = cos(aimYaw) * cos(aimPitch)
            val kind = if (w.weaponClass == WeaponClass.ROCKET_LAUNCHER) ProjectileKind.ROCKET else ProjectileKind.CROSSBOW_BOLT
            pools.spawnProjectile(
                kind, shooter.id,
                eyeX + dx * 0.8f, eyeY + dy * 0.8f, eyeZ + dz * 0.8f,
                dx * w.weaponClass.projectileSpeed, dy * w.weaponClass.projectileSpeed, dz * w.weaponClass.projectileSpeed,
                w.effectiveDamage, w.weaponClass.splashRadius, 4.5f, w.weaponClass.id
            )
            return
        }

        // Hitscan pellets (1 for rifles/snipers, 8 for Shotgun)
        val adsSpreadMult = if (shooter.isAiming) 0.35f else 1.0f
        val spreadRad = Math.toRadians((w.effectiveSpread * adsSpreadMult).toDouble()).toFloat()
        for (p in 0 until w.weaponClass.pellets) {
            val sYaw = aimYaw + (rng.nextFloat() * 2f - 1f) * spreadRad
            val sPitch = aimPitch + (rng.nextFloat() * 2f - 1f) * spreadRad
            val dx = sin(sYaw) * cos(sPitch)
            val dy = sin(sPitch)
            val dz = cos(sYaw) * cos(sPitch)

            val worldHit = world.spatialGrid.raycastWorld(eyeX, eyeY, eyeZ, dx, dy, dz, w.weaponClass.range, world.terrain)
            var hitDist = worldHit.distance
            var hitActor: Actor? = null
            var hitHeadshot = false

            // Test ray vs all alive actors
            for (i in 0 until actors.size) {
                val target = actors[i]
                if (!target.alive || target.id == shooter.id || target.dropPhase == DropPhase.IN_PLANE) continue
                val tx = target.x - eyeX
                val tz = target.z - eyeZ
                val proj = tx * dx + ((target.y + 1.0f) - eyeY) * dy + tz * dz
                if (proj < 0.5f || proj > hitDist) continue
                val closestX = eyeX + dx * proj
                val closestY = eyeY + dy * proj
                val closestZ = eyeZ + dz * proj
                val hDistSq = (closestX - target.x) * (closestX - target.x) + (closestZ - target.z) * (closestZ - target.z)
                val targetHeight = if (target.isCrouching) WorldConfig.PLAYER_CROUCH_HEIGHT else WorldConfig.PLAYER_HEIGHT
                if (hDistSq <= 0.62f * 0.62f && closestY >= target.y - 0.1f && closestY <= target.y + targetHeight + 0.18f) {
                    hitDist = proj
                    hitActor = target
                    hitHeadshot = closestY >= target.y + targetHeight - 0.42f
                }
            }

            val endX = eyeX + dx * hitDist
            val endY = eyeY + dy * hitDist
            val endZ = eyeZ + dz * hitDist
            pools.spawnTracer(eyeX, eyeY - 0.08f, eyeZ, endX, endY, endZ, shooter.trailRgb)

            if (hitActor != null) {
                shooter.shotsHit++
                val dmg = w.effectiveDamage * (if (hitHeadshot) w.weaponClass.headshotMult else 1f)
                applyDamage(hitActor, shooter, dmg, hitHeadshot, w.weaponClass.displayName, shooter.x, shooter.z, w.weaponClass.id)
                spawnImpactSparks(endX, endY, endZ, 1f, 0.2f, 0.35f)
            } else if (worldHit.hit) {
                spawnImpactSparks(endX, endY, endZ, 1f, 0.85f, 0.4f)
                val col = worldHit.collider
                if (col != null) {
                    handlePropHit(col, w.effectiveDamage, shooter)
                }
            }
        }
    }

    private fun handlePropHit(col: WorldAabb, dmg: Float, shooter: Actor) {
        when (col.kind) {
            ColliderKind.CRATE -> {
                val crate = world.crates.getOrNull(col.entityRefId) ?: return
                if (crate.broken) return
                crate.hp -= dmg
                if (crate.hp <= 0f) {
                    crate.broken = true
                    col.active = false
                    if (shooter.isHuman) cratesBrokenByHuman++
                    world.spawnRandomLoot(crate.x, crate.y + 0.35f, crate.z, false, rng)
                    world.spawnRandomLoot(crate.x + 0.8f, crate.y + 0.35f, crate.z, false, rng)
                    for (k in 0 until 8) {
                        pools.spawnParticle(
                            crate.x, crate.y + 0.7f, crate.z,
                            (rng.nextFloat() - 0.5f) * 8f, 3f + rng.nextFloat() * 4f, (rng.nextFloat() - 0.5f) * 8f,
                            0.62f, 0.44f, 0.22f, 0.26f, 0.65f
                        )
                    }
                }
            }
            ColliderKind.BARREL -> {
                val barrel = world.barrels.getOrNull(col.entityRefId) ?: return
                if (barrel.exploded) return
                barrel.hp -= dmg
                if (barrel.hp <= 0f) {
                    barrel.exploded = true
                    col.active = false
                    triggerExplosion(barrel.x, barrel.y + 0.8f, barrel.z, 11f, 110f, shooter, "Explosive Barrel")
                }
            }
            else -> Unit
        }
    }

    private fun triggerExplosion(x: Float, y: Float, z: Float, radius: Float, maxDamage: Float, owner: Actor?, sourceLabel: String) {
        val obs = getObservedActor()
        audio.playExplosion(obs.x, obs.z, obs.yaw, x, z)
        val distObs = sqrt((obs.x - x) * (obs.x - x) + (obs.z - z) * (obs.z - z))
        if (distObs < 45f) {
            cameraShake = (cameraShake + (1f - distObs / 45f) * 0.9f).coerceAtMost(1.2f)
        }
        // Fireball + smoke burst
        for (i in 0 until 20) {
            val spd = 4f + rng.nextFloat() * 14f
            val a = rng.nextFloat() * 6.28f
            pools.spawnParticle(
                x, y, z, cos(a) * spd, 4f + rng.nextFloat() * 10f, sin(a) * spd,
                1.0f, 0.35f + rng.nextFloat() * 0.45f, 0.08f, 0.55f + rng.nextFloat() * 0.45f, 0.85f, 12f
            )
        }
        // Damage nearby actors
        for (i in 0 until actors.size) {
            val target = actors[i]
            if (!target.alive || target.dropPhase != DropPhase.LANDED) continue
            val d = sqrt((target.x - x) * (target.x - x) + (target.z - z) * (target.z - z))
            if (d <= radius) {
                val falloff = (1f - d / radius).coerceIn(0.2f, 1f)
                applyDamage(target, owner, maxDamage * falloff, false, sourceLabel, x, z)
            }
        }
        // Chain-detonate nearby barrels
        for (i in 0 until world.barrels.size) {
            val b = world.barrels[i]
            if (b.exploded) continue
            val d = sqrt((b.x - x) * (b.x - x) + (b.z - z) * (b.z - z))
            if (d <= radius * 0.85f) {
                b.exploded = true
                world.spatialGrid.allColliders.firstOrNull { it.id == b.colliderId }?.active = false
                triggerExplosion(b.x, b.y + 0.8f, b.z, 10f, 95f, owner, "Chain Barrel")
            }
        }
    }

    fun applyDamage(
        victim: Actor, attacker: Actor?,
        rawDamage: Float, headshot: Boolean, weaponLabel: String,
        srcX: Float, srcZ: Float, weaponId: String = ""
    ) {
        if (!victim.alive) return
        victim.cancelHeal() // Healing cancels immediately when damaged!

        // Armor & Helmet damage mitigation
        val reduction = if (headshot) victim.helmetLevel * 0.12f else victim.armorLevel * 0.11f
        val finalDmg = (rawDamage * (1f - reduction)).coerceAtLeast(1f)
        val hitShield = victim.shield > 0f

        var rem = finalDmg
        if (victim.shield > 0f) {
            val absorbed = min(victim.shield, rem)
            victim.shield -= absorbed
            rem -= absorbed
        }
        victim.hp -= rem
        victim.lastAttackerId = attacker?.id ?: -1
        victim.lastDamageTime = matchTimeSec

        if (attacker != null) {
            attacker.damageDealt += finalDmg.toInt()
            if (attacker.isHuman) {
                if (weaponId.isNotEmpty()) {
                    humanWeaponDamage[weaponId] = (humanWeaponDamage[weaponId] ?: 0) + finalDmg.toInt()
                }
                hitMarkerAlpha = 1f
                hitMarkerHeadshot = headshot
                audio.playHit(headshot)
                pools.spawnDamageNumber(victim.x, victim.y + 2.0f, victim.z, finalDmg.toInt(), headshot, hitShield)
            }
        }

        if (victim.isHuman) {
            cameraShake = (cameraShake + 0.35f).coerceAtMost(0.95f)
            damageIndicators.add(MutableDamageIndicator(srcX, srcZ, 1f))
        }

        if (victim.hp <= 0f) {
            killActor(victim, attacker, weaponLabel, headshot)
        }
    }

    private fun killActor(victim: Actor, killer: Actor?, weaponLabel: String, headshot: Boolean) {
        if (!victim.alive) return
        val aliveBefore = actors.count { it.alive }
        victim.alive = false
        victim.hp = 0f
        victim.placement = aliveBefore
        if (victim.vehicleId >= 0) exitVehicle(victim)

        // Drop victim's active weapon + medkit
        victim.activeWeapon()?.let { w ->
            world.lootItems.add(
                WorldLootItem(world.lootItems.size + 5000, victim.x, victim.y + 0.35f, victim.z, LootKind.WEAPON, w.rarity, weaponClass = w.weaponClass)
            )
        }

        // Kill effect particles
        val fxRgb = if (killer?.isHuman == true) {
            ProgressionCatalog.COSMETIC_BY_ID[saveData.equippedKillEffect]?.primaryRgb ?: floatArrayOf(1f, 0.7f, 0.2f)
        } else floatArrayOf(1f, 0.25f, 0.45f)
        for (i in 0 until 14) {
            pools.spawnParticle(
                victim.x, victim.y + 1.0f, victim.z,
                (rng.nextFloat() - 0.5f) * 9f, 2f + rng.nextFloat() * 6f, (rng.nextFloat() - 0.5f) * 9f,
                fxRgb[0], fxRgb[1], fxRgb[2], 0.28f, 0.75f, 10f
            )
        }

        if (killer != null && killer.id != victim.id) {
            killer.kills++
            if (headshot) killer.headshots++
            if (killer.isHuman) {
                if (matchTimeSec - killer.lastKillTime <= 14f) {
                    killer.streakCount++
                } else {
                    killer.streakCount = 1
                }
                killer.lastKillTime = matchTimeSec
                if (killer.streakCount >= 2) {
                    killStreakText = when (killer.streakCount) {
                        2 -> "DOUBLE KILL!"
                        3 -> "TRIPLE KILL!"
                        4 -> "RAMPAGE!"
                        else -> "UNSTOPPABLE x${killer.streakCount}!"
                    }
                    killStreakTimer = 3.2f
                    audio.playKillStreak(killer.streakCount)
                }
            }
        }

        val entry = KillFeedEntry(
            id = nextFeedId++,
            killer = killer?.name ?: "Storm",
            victim = victim.name,
            weaponName = weaponLabel,
            isHeadshot = headshot,
            involvesHuman = victim.isHuman || (killer?.isHuman == true)
        )
        killFeed.addFirst(entry)
        while (killFeed.size > 5) killFeed.removeLast()

        val remaining = actors.count { it.alive }
        if (remaining == 1) {
            // Dramatic slow-motion on final match-deciding kill!
            slowMoTimer = 1.6f
        }

        if (victim.isHuman) {
            // Enter Spectate mode following the killer (or random alive bot)
            phase = MatchPhase.SPECTATING
            spectateIndex = if (killer != null && killer.alive) killer.id else (actors.firstOrNull { it.alive }?.id ?: 0)
        }
    }

    fun skipSpectateToResults() {
        if (!resultEmitted) {
            finishMatch()
        }
    }

    private fun cycleSpectateTarget() {
        val aliveIds = actors.filter { it.alive }.map { it.id }
        if (aliveIds.isEmpty()) return
        val cur = aliveIds.indexOf(spectateIndex)
        spectateIndex = aliveIds[(cur + 1) % aliveIds.size]
    }

    private fun finishMatch() {
        if (resultEmitted) return
        resultEmitted = true
        phase = MatchPhase.ENDED
        audio.isInMatch = false
        val report = MatchResultReport(
            placement = if (human.alive) 1 else human.placement.coerceIn(2, 30),
            totalPlayers = WorldConfig.TOTAL_PLAYERS,
            kills = human.kills,
            headshots = human.headshots,
            damageDealt = human.damageDealt,
            shotsFired = human.shotsFired,
            shotsHit = human.shotsHit,
            survivalSeconds = survivalTimeSec.toInt().coerceAtLeast(1),
            airdropsLooted = airdropsLootedByHuman,
            cratesBroken = cratesBrokenByHuman,
            vehicleDriveSec = vehicleDriveTimeSec.toInt(),
            distanceMeters = humanDistanceTraveled.toInt(),
            weaponDamageMap = humanWeaponDamage.toMap(),
            killerName = actors.getOrNull(human.lastAttackerId)?.name
        )
        hudSnapshot = hudSnapshot.copy(phase = MatchPhase.ENDED, finalReport = report)
        onMatchFinished(report)
    }

    private fun updatePools(dt: Float) {
        // Projectiles (Rockets, Crossbow bolts, Grenades)
        for (p in pools.projectiles) {
            if (!p.active) continue
            p.life -= dt
            val grav = when (p.kind) {
                ProjectileKind.ROCKET -> 0f
                ProjectileKind.CROSSBOW_BOLT -> 9.8f
                else -> 22f
            }
            p.vy -= grav * dt
            val nx = p.x + p.vx * dt
            val ny = p.y + p.vy * dt
            val nz = p.z + p.vz * dt
            val owner = actors.getOrNull(p.ownerId)
            val groundY = world.terrain.getHeight(nx, nz)

            // Trail smoke for rockets
            if (p.kind == ProjectileKind.ROCKET && rng.nextFloat() < 0.7f) {
                pools.spawnParticle(p.x, p.y, p.z, 0f, 0.5f, 0f, 0.9f, 0.75f, 0.55f, 0.25f, 0.35f, 0f, true)
            }

            var detonated = p.life <= 0f
            if (p.kind == ProjectileKind.ROCKET || p.kind == ProjectileKind.CROSSBOW_BOLT) {
                // Direct hit check against actors
                for (i in 0 until actors.size) {
                    val t = actors[i]
                    if (!t.alive || t.id == p.ownerId || t.dropPhase != DropPhase.LANDED) continue
                    val dSq = (t.x - nx) * (t.x - nx) + (t.z - nz) * (t.z - nz)
                    if (dSq < 1.1f * 1.1f && ny >= t.y && ny <= t.y + 2.0f) {
                        if (p.kind == ProjectileKind.CROSSBOW_BOLT) {
                            applyDamage(t, owner, p.damage, ny > t.y + 1.4f, "Shadow Crossbow", p.x, p.z, p.weaponId)
                        }
                        detonated = true
                        break
                    }
                }
                if (ny <= groundY + 0.1f) detonated = true
            } else {
                // Grenade ground bounce
                if (ny <= groundY + 0.15f) {
                    p.y = groundY + 0.15f
                    p.vy = abs(p.vy) * 0.42f
                    p.vx *= 0.65f; p.vz *= 0.65f
                } else {
                    p.x = nx; p.y = ny; p.z = nz
                }
            }

            if (p.kind == ProjectileKind.ROCKET || p.kind == ProjectileKind.CROSSBOW_BOLT) {
                p.x = nx; p.y = ny; p.z = nz
            }

            if (detonated) {
                p.active = false
                when (p.kind) {
                    ProjectileKind.ROCKET -> triggerExplosion(p.x, p.y, p.z, p.radius, p.damage, owner, "Nova Launcher")
                    ProjectileKind.FRAG -> triggerExplosion(p.x, p.y, p.z, p.radius, 105f, owner, "Frag Grenade")
                    ProjectileKind.SMOKE -> pools.spawnSmokeCloud(p.x, p.y + 1.2f, p.z, p.radius, 12f)
                    ProjectileKind.FLASH -> {
                        val obs = getObservedActor()
                        audio.playExplosion(obs.x, obs.z, obs.yaw, p.x, p.z)
                        for (a in actors) {
                            if (!a.alive) continue
                            val d = sqrt((a.x - p.x) * (a.x - p.x) + (a.z - p.z) * (a.z - p.z))
                            if (d < p.radius) {
                                a.flashBlindTimer = (1f - d / p.radius) * 3.5f
                            }
                        }
                    }
                    ProjectileKind.CROSSBOW_BOLT -> spawnImpactSparks(p.x, p.y, p.z, 0.8f, 0.9f, 1f)
                }
            }
        }

        // Tracers
        for (t in pools.tracers) {
            if (!t.active) continue
            t.life -= dt
            if (t.life <= 0f) t.active = false
        }

        // Particles
        for (p in pools.particles) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.active = false
                continue
            }
            p.vy -= p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
        }

        // Smoke clouds
        for (s in pools.smokeClouds) {
            if (!s.active) continue
            s.life -= dt
            if (s.life <= 0f) s.active = false
        }

        // Damage numbers
        for (d in pools.damageNumbers) {
            if (!d.active) continue
            d.life -= dt
            d.worldY += 1.4f * dt
            if (d.life <= 0f) d.active = false
        }
    }

    private fun spawnImpactSparks(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float) {
        val count = (4 * settings.qualityEnum().particleMultiplier).toInt().coerceAtLeast(2)
        for (i in 0 until count) {
            pools.spawnParticle(
                x, y, z,
                (rng.nextFloat() - 0.5f) * 7f, 1.5f + rng.nextFloat() * 4f, (rng.nextFloat() - 0.5f) * 7f,
                r, g, b, 0.14f, 0.30f, 18f
            )
        }
    }

    private fun spawnLandingEffect(a: Actor) {
        val rgb = if (a.isHuman) {
            ProgressionCatalog.COSMETIC_BY_ID[saveData.equippedLandingEffect]?.primaryRgb ?: floatArrayOf(0.8f, 0.78f, 0.72f)
        } else floatArrayOf(0.75f, 0.73f, 0.68f)
        for (i in 0 until 10) {
            val ang = (i.toFloat() / 10f) * 6.28f
            pools.spawnParticle(
                a.x, a.y + 0.2f, a.z,
                cos(ang) * 5.5f, 1.2f + rng.nextFloat() * 1.5f, sin(ang) * 5.5f,
                rgb[0], rgb[1], rgb[2], 0.36f, 0.55f, 3f, true
            )
        }
    }

    fun startReload(a: Actor) {
        val w = a.activeWeapon() ?: return
        if (a.reloading || w.magAmmo >= w.effectiveMagSize) return
        if (a.ammoReserves[w.weaponClass.ammoType.ordinal] <= 0) return
        a.cancelHeal()
        a.reloading = true
        a.reloadTimer = w.effectiveReloadTime
        if (a.isHuman) audio.playReload()
    }

    fun startHealing(a: Actor) {
        if (a.healingType != null) return
        val item = a.bestConsumable() ?: return
        a.reloading = false
        a.healingType = item
        a.healingTimer = 0f
        a.healingDuration = item.channelSeconds
        if (a.isHuman) audio.playUiClick()
    }

    private fun throwGrenade(a: Actor) {
        val g = a.selectedGrenade
        if (a.grenades[g.ordinal] <= 0) return
        a.grenades[g.ordinal]--
        val dx = sin(a.yaw) * cos(a.pitch)
        val dy = sin(a.pitch) + 0.35f
        val dz = cos(a.yaw) * cos(a.pitch)
        val kind = when (g) {
            GrenadeType.FRAG -> ProjectileKind.FRAG
            GrenadeType.SMOKE -> ProjectileKind.SMOKE
            GrenadeType.FLASH -> ProjectileKind.FLASH
        }
        pools.spawnProjectile(
            kind, a.id,
            a.x + dx * 0.8f, a.y + 1.5f, a.z + dz * 0.8f,
            dx * 24f, dy * 24f, dz * 24f,
            100f, g.radius, g.fuseTime, g.name
        )
        if (a.isHuman) audio.playUiClick()
    }

    private fun performMelee(a: Actor) {
        a.shootAnimTimer = 0.18f
        if (a.isHuman) audio.playUiClick()
        for (i in 0 until actors.size) {
            val t = actors[i]
            if (!t.alive || t.id == a.id || t.dropPhase != DropPhase.LANDED) continue
            val dSq = (t.x - a.x) * (t.x - a.x) + (t.z - a.z) * (t.z - a.z)
            if (dSq < 3.2f * 3.2f) {
                applyDamage(t, a, 38f, false, "Melee Strike", a.x, a.z)
            }
        }
    }

    fun tryToggleVehicle(a: Actor): Boolean {
        if (a.vehicleId >= 0) {
            exitVehicle(a)
            return true
        }
        val veh = world.vehicles.firstOrNull {
            !it.exploded && it.driverId < 0 &&
                (it.x - a.x) * (it.x - a.x) + (it.z - a.z) * (it.z - a.z) < 5.5f * 5.5f
        } ?: return false
        veh.driverId = a.id
        a.vehicleId = veh.id
        a.cancelHeal()
        if (a.isHuman) audio.playPickup()
        return true
    }

    private fun exitVehicle(a: Actor) {
        val veh = world.vehicles.getOrNull(a.vehicleId)
        if (veh != null) {
            veh.driverId = -1
            a.x = veh.x + cos(veh.yaw) * 2.6f
            a.z = veh.z - sin(veh.yaw) * 2.6f
            a.y = world.terrain.getHeight(a.x, a.z) + 0.2f
        }
        a.vehicleId = -1
    }

    fun findNearestLoot(x: Float, z: Float, maxDist: Float, weaponsOnly: Boolean): WorldLootItem? {
        var best: WorldLootItem? = null
        var bestSq = maxDist * maxDist
        for (i in 0 until world.lootItems.size) {
            val item = world.lootItems[i]
            if (item.pickedUp) continue
            if (weaponsOnly && item.kind != LootKind.WEAPON) continue
            val dSq = (item.x - x) * (item.x - x) + (item.z - z) * (item.z - z)
            if (dSq < bestSq) {
                bestSq = dSq
                best = item
            }
        }
        return best
    }

    fun tryBotQuickLoot(bot: Actor) {
        val item = findNearestLoot(bot.x, bot.z, 4.5f, weaponsOnly = false) ?: return
        applyLootPickup(bot, item)
    }

    fun tryPickupNearestLoot(a: Actor): Boolean {
        val item = findNearestLoot(a.x, a.z, 4.8f, weaponsOnly = false) ?: return false
        applyLootPickup(a, item)
        if (a.isHuman) audio.playPickup()
        return true
    }

    fun tryOpenNearbyAirdrop(a: Actor): Boolean {
        val drop = airdrops.firstOrNull {
            it.landed && !it.looted &&
                (it.x - a.x) * (it.x - a.x) + (it.z - a.z) * (it.z - a.z) < 5.5f * 5.5f
        } ?: return false
        drop.looted = true
        if (a.isHuman) {
            airdropsLootedByHuman++
            audio.playPickup()
        }
        // Legendary weapon + big shield + Lv3 armor!
        val legendaries = listOf(WeaponClass.SNIPER, WeaponClass.ROCKET_LAUNCHER, WeaponClass.ASSAULT_RIFLE, WeaponClass.LMG)
        val wc = legendaries[rng.nextInt(legendaries.size)]
        val inst = WeaponInstance(wc, Rarity.LEGENDARY).apply {
            attachments.add(AttachmentType.EXTENDED_MAG)
            attachments.add(AttachmentType.GRIP)
            magAmmo = effectiveMagSize
        }
        val emptySlot = a.weaponSlots.indexOfFirst { it == null }
        val slot = if (emptySlot >= 0) emptySlot else a.activeSlot
        a.weaponSlots[slot] = inst
        a.activeSlot = slot
        a.ammoReserves[wc.ammoType.ordinal] += wc.ammoType.pickupAmount * 3
        a.armorLevel = 3
        a.helmetLevel = 3
        a.consumables[ConsumableType.MEDKIT.ordinal]++
        a.consumables[ConsumableType.BIG_SHIELD.ordinal] += 2
        return true
    }

    private fun applyLootPickup(a: Actor, item: WorldLootItem) {
        item.pickedUp = true
        when (item.kind) {
            LootKind.WEAPON -> {
                val wc = item.weaponClass ?: return
                val inst = WeaponInstance(wc, item.rarity)
                val emptySlot = a.weaponSlots.indexOfFirst { it == null }
                val targetSlot = if (emptySlot >= 0) emptySlot else a.activeSlot
                a.weaponSlots[targetSlot] = inst
                if (a.activeWeapon() == null || item.rarity.ordinal > (a.activeWeapon()?.rarity?.ordinal ?: -1)) {
                    a.activeSlot = targetSlot
                }
                a.ammoReserves[wc.ammoType.ordinal] = (a.ammoReserves[wc.ammoType.ordinal] + wc.ammoType.pickupAmount).coerceAtMost(a.maxAmmoCapacity())
            }
            LootKind.AMMO -> {
                val at = item.ammoType ?: return
                a.ammoReserves[at.ordinal] = (a.ammoReserves[at.ordinal] + item.ammoAmount).coerceAtMost(a.maxAmmoCapacity())
            }
            LootKind.ATTACHMENT -> {
                val att = item.attachment ?: return
                a.activeWeapon()?.attachments?.add(att)
            }
            LootKind.CONSUMABLE -> {
                val c = item.consumable ?: return
                a.consumables[c.ordinal]++
            }
            LootKind.GRENADE -> {
                val g = item.grenade ?: return
                a.grenades[g.ordinal]++
                a.selectedGrenade = g
            }
            LootKind.ARMOR -> if (item.gearLevel > a.armorLevel) a.armorLevel = item.gearLevel
            LootKind.HELMET -> if (item.gearLevel > a.helmetLevel) a.helmetLevel = item.gearLevel
            LootKind.BACKPACK -> if (item.gearLevel > a.backpackLevel) a.backpackLevel = item.gearLevel
        }
    }

    private fun publishHudSnapshot(projectedDmg: List<ScreenDamageNumber>) {
        val obs = getObservedActor()
        val slotsUi = (0..4).map { idx ->
            val w = obs.weaponSlots[idx]
            WeaponSlotUi(
                slotIndex = idx,
                weaponClass = w?.weaponClass,
                rarity = w?.rarity,
                magAmmo = w?.magAmmo ?: 0,
                maxMag = w?.effectiveMagSize ?: 0,
                reserveAmmo = if (w != null) obs.ammoReserves[w.weaponClass.ammoType.ordinal] else 0,
                attachments = w?.attachments?.toList() ?: emptyList()
            )
        }

        val activeW = obs.activeWeapon()
        val scopeMode = if (obs.isHuman && obs.isAiming && activeW != null) {
            when {
                activeW.hasScope -> ScopeMode.SNIPER
                activeW.hasRedDot -> ScopeMode.RED_DOT
                else -> ScopeMode.NONE
            }
        } else ScopeMode.NONE

        // Nearby interact prompt (airdrop / loot / vehicle)
        val nearDrop = airdrops.firstOrNull {
            it.landed && !it.looted && (it.x - human.x) * (it.x - human.x) + (it.z - human.z) * (it.z - human.z) < 5.5f * 5.5f
        }
        val nearLoot = if (nearDrop == null && human.alive && human.dropPhase == DropPhase.LANDED) {
            findNearestLoot(human.x, human.z, 4.8f, weaponsOnly = false)
        } else null
        val nearVeh = human.alive && human.dropPhase == DropPhase.LANDED && human.vehicleId < 0 &&
            world.vehicles.any { !it.exploded && it.driverId < 0 && (it.x - human.x) * (it.x - human.x) + (it.z - human.z) * (it.z - human.z) < 5.5f * 5.5f }

        val prompt = when {
            nearDrop != null -> "OPEN LEGENDARY AIRDROP"
            nearLoot != null -> "PICK UP ${nearLoot.displayLabel().uppercase()}"
            else -> null
        }

        val bestHeal = obs.bestConsumable()
        val veh = if (obs.vehicleId >= 0) world.vehicles.getOrNull(obs.vehicleId) else null

        // Directional damage indicator angles relative to human forward
        val dmgInd = damageIndicators.map { di ->
            val angToSrc = atan2(di.srcX - human.x, di.srcZ - human.z)
            DamageIndicatorUi(angToSrc - human.yaw, di.alpha.coerceIn(0f, 1f))
        }

        // Minimap dots (airdrops, nearby vehicles, recent gunshots)
        val dots = ArrayList<MinimapDot>(24)
        for (d in airdrops) {
            if (!d.looted) dots.add(MinimapDot(d.x, d.z, isAirdrop = true))
        }
        for (v in world.vehicles) {
            if (!v.exploded && (v.x - obs.x) * (v.x - obs.x) + (v.z - obs.z) * (v.z - obs.z) < 350f * 350f) {
                dots.add(MinimapDot(v.x, v.z, isVehicle = true))
            }
        }
        for (s in recentShotBlips) {
            dots.add(MinimapDot(s.x, s.z, isEnemyShot = true))
        }

        val groundY = world.terrain.getHeight(obs.x, obs.z)
        hudSnapshot = MatchHudState(
            phase = phase,
            matchmakingFound = 30,
            dropPhase = obs.dropPhase,
            altitudeMeters = (obs.y - groundY).toInt().coerceAtLeast(0),
            timeOfDay = timeOfDay,
            flashlightOn = obs.flashlightOn,
            hp = obs.hp.coerceIn(0f, WorldConfig.MAX_HP),
            shield = obs.shield.coerceIn(0f, WorldConfig.MAX_SHIELD),
            stamina = obs.stamina.coerceIn(0f, WorldConfig.MAX_STAMINA),
            armorLevel = obs.armorLevel,
            helmetLevel = obs.helmetLevel,
            backpackLevel = obs.backpackLevel,
            isCrouching = obs.isCrouching,
            isSprinting = obs.isSprinting,
            isHiddenInBush = obs.isHiddenInBush,
            isSwimming = obs.isSwimming,
            speedBoostActive = obs.speedBoostTimer > 0f,
            inVehicle = veh != null,
            vehicleHp = veh?.hp ?: 0f,
            vehicleMaxHp = veh?.maxHp ?: 320f,
            vehicleSpeedKmh = ((abs(veh?.speed ?: 0f)) * 3.6f).toInt(),
            activeSlot = obs.activeSlot,
            slots = slotsUi,
            reloading = obs.reloading,
            reloadProgress = if (obs.reloading && activeW != null) (1f - obs.reloadTimer / activeW.effectiveReloadTime).coerceIn(0f, 1f) else 0f,
            healingLabel = obs.healingType?.label,
            healingProgress = if (obs.healingType != null && obs.healingDuration > 0f) (obs.healingTimer / obs.healingDuration).coerceIn(0f, 1f) else 0f,
            availableHealLabel = bestHeal?.label,
            availableHealCount = if (bestHeal != null) obs.consumables[bestHeal.ordinal] else 0,
            selectedGrenade = obs.selectedGrenade,
            grenadeCount = obs.grenades[obs.selectedGrenade.ordinal],
            interactPrompt = prompt,
            canEnterVehicle = nearVeh || veh != null,
            scopeMode = scopeMode,
            playersAlive = actors.count { it.alive },
            kills = human.kills,
            stormStage = stormStageIdx + 1,
            stormShrinking = stormShrinking,
            stormTimerSec = stormStageTimer.toInt().coerceAtLeast(0),
            inStorm = isInStorm(obs.x, obs.z),
            stormCenterX = stormCenterX,
            stormCenterZ = stormCenterZ,
            stormRadius = stormRadius,
            nextStormCenterX = nextStormX,
            nextStormCenterZ = nextStormZ,
            nextStormRadius = nextStormRadius,
            playerX = obs.x,
            playerZ = obs.z,
            playerYawRad = obs.yaw,
            gridSquare = world.roadNetwork.gridCodeFor(obs.x, obs.z),
            waypointX = waypointX,
            waypointZ = waypointZ,
            planeStartX = planeStartX,
            planeStartZ = planeStartZ,
            planeEndX = planeEndX,
            planeEndZ = planeEndZ,
            planeX = planeX,
            planeZ = planeZ,
            hitMarkerAlpha = hitMarkerAlpha,
            hitMarkerHeadshot = hitMarkerHeadshot,
            flashBlindAlpha = (obs.flashBlindTimer / 3.5f).coerceIn(0f, 1f),
            killStreakText = killStreakText,
            airdropBannerText = airdropBannerText,
            slowMoActive = slowMoTimer > 0f,
            spectateTargetName = if (phase == MatchPhase.SPECTATING) obs.name else null,
            spectateTargetHp = obs.hp,
            killFeed = killFeed.toList(),
            damageIndicators = dmgInd,
            floatingDamageNumbers = projectedDmg,
            minimapDots = dots,
            minimapPois = world.pois.map { MinimapPoi(it.name, it.x, it.z, it.highTierLoot) },
            roadSegments = world.roadNetwork.segments,
            buildingFootprints = world.buildingFootprints,
            fps = currentFps,
            finalReport = hudSnapshot.finalReport
        )
    }
}
