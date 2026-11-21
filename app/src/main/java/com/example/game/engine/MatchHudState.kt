package com.example.game.engine

import com.example.game.config.AttachmentType
import com.example.game.config.ConsumableType
import com.example.game.config.GrenadeType
import com.example.game.config.Rarity
import com.example.game.config.TimeOfDay
import com.example.game.config.WeaponClass
import com.example.game.entities.DropPhase
import com.example.game.progression.MatchResultReport
import com.example.game.world.CityBlockFootprint
import com.example.game.world.RoadSegment

// ============================================================================
// SECTION: HUD SNAPSHOT — Throttled Immutable UI State for Jetpack Compose
// ============================================================================

enum class MatchPhase {
    MATCHMAKING,
    IN_PLANE,
    ACTIVE,
    SPECTATING,
    ENDED
}

enum class ScopeMode {
    NONE,
    RED_DOT,
    SNIPER
}

data class KillFeedEntry(
    val id: Long,
    val killer: String,
    val victim: String,
    val weaponName: String,
    val isHeadshot: Boolean,
    val involvesHuman: Boolean
)

data class WeaponSlotUi(
    val slotIndex: Int,
    val weaponClass: WeaponClass?,
    val rarity: Rarity?,
    val magAmmo: Int,
    val maxMag: Int,
    val reserveAmmo: Int,
    val attachments: List<AttachmentType>
)

data class DamageIndicatorUi(
    val angleRad: Float, // relative to player forward (0 = front, PI = behind)
    val alpha: Float
)

data class ScreenDamageNumber(
    val id: Int,
    val normX: Float, // 0..1 screen space
    val normY: Float,
    val amount: Int,
    val isHeadshot: Boolean,
    val isShield: Boolean,
    val alpha: Float
)

data class MinimapDot(
    val x: Float,
    val z: Float,
    val isAirdrop: Boolean = false,
    val isVehicle: Boolean = false,
    val isEnemyShot: Boolean = false
)

data class MinimapPoi(
    val name: String,
    val x: Float,
    val z: Float,
    val highTier: Boolean
)

data class MatchHudState(
    val phase: MatchPhase = MatchPhase.MATCHMAKING,
    val matchmakingFound: Int = 1,
    val dropPhase: DropPhase = DropPhase.IN_PLANE,
    val altitudeMeters: Int = 260,
    val timeOfDay: TimeOfDay = TimeOfDay.DAY,
    val flashlightOn: Boolean = false,
    val hp: Float = 100f,
    val shield: Float = 0f,
    val stamina: Float = 100f,
    val armorLevel: Int = 0,
    val helmetLevel: Int = 0,
    val backpackLevel: Int = 0,
    val isCrouching: Boolean = false,
    val isSprinting: Boolean = false,
    val isHiddenInBush: Boolean = false,
    val isSwimming: Boolean = false,
    val speedBoostActive: Boolean = false,
    val inVehicle: Boolean = false,
    val vehicleHp: Float = 0f,
    val vehicleMaxHp: Float = 320f,
    val vehicleSpeedKmh: Int = 0,
    val activeSlot: Int = 0,
    val slots: List<WeaponSlotUi> = emptyList(),
    val reloading: Boolean = false,
    val reloadProgress: Float = 0f,
    val healingLabel: String? = null,
    val healingProgress: Float = 0f,
    val availableHealLabel: String? = null,
    val availableHealCount: Int = 0,
    val selectedGrenade: GrenadeType = GrenadeType.FRAG,
    val grenadeCount: Int = 0,
    val interactPrompt: String? = null,
    val canEnterVehicle: Boolean = false,
    val scopeMode: ScopeMode = ScopeMode.NONE,
    val playersAlive: Int = 30,
    val kills: Int = 0,
    val stormStage: Int = 1,
    val stormShrinking: Boolean = false,
    val stormTimerSec: Int = 22,
    val inStorm: Boolean = false,
    val stormCenterX: Float = 0f,
    val stormCenterZ: Float = 0f,
    val stormRadius: Float = 980f,
    val nextStormCenterX: Float = 0f,
    val nextStormCenterZ: Float = 0f,
    val nextStormRadius: Float = 640f,
    val playerX: Float = 0f,
    val playerZ: Float = 0f,
    val playerYawRad: Float = 0f,
    val gridSquare: String = "D4",
    val waypointX: Float? = null,
    val waypointZ: Float? = null,
    val planeStartX: Float = -900f,
    val planeStartZ: Float = -200f,
    val planeEndX: Float = 900f,
    val planeEndZ: Float = 200f,
    val planeX: Float = 0f,
    val planeZ: Float = 0f,
    val hitMarkerAlpha: Float = 0f,
    val hitMarkerHeadshot: Boolean = false,
    val flashBlindAlpha: Float = 0f,
    val killStreakText: String? = null,
    val airdropBannerText: String? = null,
    val slowMoActive: Boolean = false,
    val spectateTargetName: String? = null,
    val spectateTargetHp: Float = 100f,
    val killFeed: List<KillFeedEntry> = emptyList(),
    val damageIndicators: List<DamageIndicatorUi> = emptyList(),
    val floatingDamageNumbers: List<ScreenDamageNumber> = emptyList(),
    val minimapDots: List<MinimapDot> = emptyList(),
    val minimapPois: List<MinimapPoi> = emptyList(),
    val roadSegments: List<RoadSegment> = emptyList(),
    val buildingFootprints: List<CityBlockFootprint> = emptyList(),
    val fps: Int = 60,
    val finalReport: MatchResultReport? = null
)
