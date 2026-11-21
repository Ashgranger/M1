package com.example.game.save

import com.example.game.config.GraphicsQuality
import com.squareup.moshi.JsonClass

// ============================================================================
// SECTION: SAVE — Serializable Local Progression, Settings & Custom HUD Layout
// ============================================================================

@JsonClass(generateAdapter = true)
data class HudButtonLayout(
    val xFrac: Float, // 0..1 across width
    val yFrac: Float, // 0..1 across height
    val scale: Float = 1.0f
)

@JsonClass(generateAdapter = true)
data class GameSettings(
    val soundVolume: Float = 0.85f,
    val musicVolume: Float = 0.45f,
    val lookSensitivity: Float = 1.0f,
    val invertY: Boolean = false,
    val graphicsQuality: String = GraphicsQuality.MEDIUM.name,
    val showFps: Boolean = true,
    val aimAssistEnabled: Boolean = true,
    val gyroEnabled: Boolean = false,
    val hudLayout: Map<String, HudButtonLayout> = defaultHudLayout()
) {
    fun qualityEnum(): GraphicsQuality =
        runCatching { GraphicsQuality.valueOf(graphicsQuality) }.getOrDefault(GraphicsQuality.MEDIUM)

    companion object {
        fun defaultHudLayout(): Map<String, HudButtonLayout> = mapOf(
            "fire" to HudButtonLayout(0.86f, 0.72f, 1.15f),
            "aim" to HudButtonLayout(0.75f, 0.62f, 0.95f),
            "jump" to HudButtonLayout(0.92f, 0.48f, 0.95f),
            "crouch" to HudButtonLayout(0.84f, 0.42f, 0.9f),
            "reload" to HudButtonLayout(0.74f, 0.82f, 0.9f),
            "pickup" to HudButtonLayout(0.65f, 0.66f, 0.95f),
            "grenade" to HudButtonLayout(0.66f, 0.83f, 0.88f),
            "heal" to HudButtonLayout(0.28f, 0.82f, 0.9f),
            "sprint" to HudButtonLayout(0.14f, 0.44f, 0.88f),
            "vehicle" to HudButtonLayout(0.56f, 0.72f, 0.9f)
        )
    }
}

@JsonClass(generateAdapter = true)
data class ChallengeProgress(
    val id: String,
    val title: String,
    val metric: String, // KILLS, DAMAGE, MATCHES, TOP5, SURVIVE_MIN, HEADSHOTS, LOOT, AIRDROPS
    val target: Int,
    val current: Int = 0,
    val rewardCoins: Int = 150,
    val rewardXp: Int = 200,
    val isWeekly: Boolean = false,
    val claimed: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CareerStats(
    val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val totalKills: Int = 0,
    val totalDeaths: Int = 0,
    val totalDamage: Int = 0,
    val totalHeadshots: Int = 0,
    val shotsFired: Int = 0,
    val shotsHit: Int = 0,
    val bestPlacement: Int = 30,
    val bestKillsInMatch: Int = 0,
    val bestDamageInMatch: Int = 0,
    val longestSurvivalSec: Int = 0,
    val airdropsLooted: Int = 0,
    val cratesBroken: Int = 0,
    val vehiclesDrivenSec: Int = 0,
    val distanceTraveledMeters: Int = 0,
    val winStreak: Int = 0,
    val bestWinStreak: Int = 0
) {
    val kdRatio: Float get() = if (totalDeaths == 0) totalKills.toFloat() else totalKills.toFloat() / totalDeaths.toFloat()
    val accuracyPct: Float get() = if (shotsFired == 0) 0f else (shotsHit.toFloat() / shotsFired.toFloat()) * 100f
}

@JsonClass(generateAdapter = true)
data class PlayerSaveData(
    val playerName: String = "OPERATIVE",
    val level: Int = 1,
    val xp: Int = 0,
    val rp: Int = 100,
    val coins: Int = 650,
    val gems: Int = 120,
    val battlePassXp: Int = 0,
    val battlePassPremium: Boolean = false,
    val claimedFreeBpTiers: List<Int> = emptyList(),
    val claimedPremiumBpTiers: List<Int> = emptyList(),
    val ownedCosmetics: List<String> = listOf(
        "skin_cyber_scout", "wskin_carbon", "trail_cyan", "chute_standard",
        "land_dust", "kill_sparks", "emote_salute"
    ),
    val equippedCharacterSkin: String = "skin_cyber_scout",
    val equippedWeaponSkin: String = "wskin_carbon",
    val equippedBulletTrail: String = "trail_cyan",
    val equippedParachute: String = "chute_standard",
    val equippedLandingEffect: String = "land_dust",
    val equippedKillEffect: String = "kill_sparks",
    val equippedEmote: String = "emote_salute",
    val shopDayKey: Long = 0L,
    val dailyChallengesDayKey: Long = 0L,
    val weeklyChallengesWeekKey: Long = 0L,
    val dailyChallenges: List<ChallengeProgress> = emptyList(),
    val weeklyChallenges: List<ChallengeProgress> = emptyList(),
    val loginStreakDay: Int = 1,
    val lastLoginDayKey: Long = 0L,
    val claimedLoginDays: List<Int> = emptyList(),
    val lastLuckySpinMs: Long = 0L,
    val unlockedAchievements: List<String> = emptyList(),
    val claimedAchievements: List<String> = emptyList(),
    val weaponMasteryXp: Map<String, Int> = emptyMap(),
    val claimedMasteryRewards: List<String> = emptyList(),
    val stats: CareerStats = CareerStats(),
    val settings: GameSettings = GameSettings()
)
