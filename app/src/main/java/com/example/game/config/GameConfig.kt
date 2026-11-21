package com.example.game.config

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.RarityCommon
import com.example.ui.theme.RarityEpic
import com.example.ui.theme.RarityLegendary
import com.example.ui.theme.RarityRare
import com.example.ui.theme.RarityUncommon

// ============================================================================
// SECTION: CONFIG — World, Weapons, Rarities, Storm, Biomes, Ranks & Bots
// ============================================================================

object WorldConfig {
    const val WORLD_SIZE = 2000f
    const val HALF_WORLD = WORLD_SIZE / 2f
    const val WATER_LEVEL = 0f
    const val SWIM_DEPTH = -0.8f
    const val TOTAL_PLAYERS = 30
    const val BOT_COUNT = 29
    const val MAX_DELTA_TIME = 0.05f
    const val GRAVITY = 28f
    const val PLANE_ALTITUDE = 260f
    const val PARACHUTE_OPEN_ALTITUDE = 68f
    const val BASE_MOVE_SPEED = 8.4f
    const val SPRINT_MULTIPLIER = 1.48f
    const val CROUCH_MULTIPLIER = 0.56f
    const val SLIDE_SPEED = 14.5f
    const val SWIM_MULTIPLIER = 0.55f
    const val JUMP_VELOCITY = 10.6f
    const val PLAYER_HEIGHT = 1.85f
    const val PLAYER_CROUCH_HEIGHT = 1.15f
    const val PLAYER_RADIUS = 0.48f
    const val MAX_HP = 100f
    const val MAX_SHIELD = 100f
    const val MAX_STAMINA = 100f
}

enum class GraphicsQuality(
    val label: String,
    val drawDistance: Float,
    val treeDensity: Float,
    val particleMultiplier: Float,
    val terrainStep: Int,
    val shadowsEnabled: Boolean
) {
    LOW("Low", 260f, 0.45f, 0.5f, 2, false),
    MEDIUM("Medium", 420f, 0.75f, 0.8f, 1, true),
    HIGH("High", 620f, 1.0f, 1.0f, 1, true)
}

enum class Biome(val label: String, val footstepPitch: Float, val speedMod: Float) {
    FOREST("Forest", 1.0f, 1.0f),
    DESERT("Desert", 0.85f, 0.98f),
    SNOW("Snow", 1.18f, 0.94f),
    SWAMP("Swamp", 0.72f, 0.78f),
    WATER("Water", 0.65f, 0.58f)
}

enum class TimeOfDay(
    val label: String,
    val skyTop: FloatArray,
    val skyHorizon: FloatArray,
    val sunColor: FloatArray,
    val ambientStrength: Float,
    val fogColor: FloatArray,
    val isNight: Boolean
) {
    DAY(
        "Clear Daylight",
        floatArrayOf(0.22f, 0.50f, 0.78f),
        floatArrayOf(0.72f, 0.80f, 0.86f),
        floatArrayOf(1.0f, 0.96f, 0.88f),
        0.46f,
        floatArrayOf(0.68f, 0.75f, 0.80f),
        false
    ),
    SUNSET(
        "Golden Hour",
        floatArrayOf(0.24f, 0.34f, 0.54f),
        floatArrayOf(0.92f, 0.62f, 0.38f),
        floatArrayOf(1.0f, 0.74f, 0.44f),
        0.38f,
        floatArrayOf(0.74f, 0.56f, 0.42f),
        false
    ),
    NIGHT(
        "Tactical Dusk",
        floatArrayOf(0.04f, 0.07f, 0.14f),
        floatArrayOf(0.14f, 0.18f, 0.28f),
        floatArrayOf(0.55f, 0.64f, 0.82f),
        0.26f,
        floatArrayOf(0.10f, 0.13f, 0.21f),
        true
    )
}

enum class Rarity(
    val label: String,
    val color: Color,
    val rgb: FloatArray,
    val statMultiplier: Float,
    val dropWeight: Int
) {
    COMMON("Common", RarityCommon, floatArrayOf(0.62f, 0.64f, 0.69f), 1.00f, 44),
    UNCOMMON("Uncommon", RarityUncommon, floatArrayOf(0.13f, 0.77f, 0.37f), 1.10f, 28),
    RARE("Rare", RarityRare, floatArrayOf(0.23f, 0.51f, 0.96f), 1.22f, 16),
    EPIC("Epic", RarityEpic, floatArrayOf(0.66f, 0.33f, 0.97f), 1.35f, 9),
    LEGENDARY("Legendary", RarityLegendary, floatArrayOf(0.96f, 0.62f, 0.04f), 1.50f, 3)
}

enum class AmmoType(val label: String, val shortName: String, val pickupAmount: Int) {
    LIGHT("Light Ammo", "9MM", 32),
    MEDIUM("Medium Ammo", "5.56", 28),
    HEAVY("Heavy Ammo", ".50", 12),
    SHELLS("Shotgun Shells", "12G", 10),
    ROCKETS("Rockets", "RPG", 3)
}

enum class AttachmentType(val label: String, val shortCode: String) {
    SCOPE("4x Sniper Scope", "SCP"),
    RED_DOT("Red Dot Sight", "RDS"),
    SILENCER("Tactical Silencer", "SIL"),
    EXTENDED_MAG("Extended Mag", "EXT"),
    GRIP("Vertical Grip", "GRP")
}

enum class WeaponClass(
    val id: String,
    val displayName: String,
    val ammoType: AmmoType,
    val baseDamage: Float,
    val fireRate: Float,        // rounds per second
    val spreadDeg: Float,
    val recoilKick: Float,
    val magSize: Int,
    val reloadTime: Float,
    val isHitscan: Boolean,
    val projectileSpeed: Float,
    val range: Float,
    val headshotMult: Float,
    val pellets: Int = 1,
    val splashRadius: Float = 0f,
    val hasDefaultScope: Boolean = false,
    val hasDefaultRedDot: Boolean = false,
    val soundRadius: Float = 180f,
    val autoFire: Boolean = true
) {
    PISTOL("pistol", "Pulse Pistol", AmmoType.LIGHT, 21f, 4.8f, 1.8f, 0.9f, 15, 1.25f, true, 0f, 110f, 1.85f, autoFire = false),
    REVOLVER("revolver", "Magnum .44", AmmoType.HEAVY, 44f, 1.6f, 1.1f, 2.4f, 6, 1.75f, true, 0f, 145f, 2.1f, autoFire = false),
    SMG("smg", "Vector SMG", AmmoType.LIGHT, 15f, 11.5f, 2.8f, 0.75f, 30, 1.6f, true, 0f, 95f, 1.65f),
    ASSAULT_RIFLE("ar", "Valkyrie AR", AmmoType.MEDIUM, 24f, 6.8f, 1.5f, 1.2f, 30, 2.0f, true, 0f, 210f, 1.85f, hasDefaultRedDot = true),
    LMG("lmg", "Titan LMG", AmmoType.MEDIUM, 22f, 8.2f, 3.0f, 1.5f, 65, 3.2f, true, 0f, 190f, 1.7f),
    SHOTGUN("shotgun", "Striker Pump", AmmoType.SHELLS, 14f, 1.15f, 6.5f, 3.2f, 6, 2.2f, true, 0f, 42f, 1.5f, pellets = 8, autoFire = false),
    SNIPER("sniper", "Phantom Sniper", AmmoType.HEAVY, 86f, 0.68f, 0.15f, 3.8f, 5, 2.5f, true, 0f, 420f, 2.35f, hasDefaultScope = true, soundRadius = 280f, autoFire = false),
    MARKSMAN("marksman", "DMR Specter", AmmoType.MEDIUM, 46f, 2.8f, 0.65f, 1.9f, 16, 2.05f, true, 0f, 310f, 2.05f, hasDefaultScope = true, autoFire = false),
    CROSSBOW("crossbow", "Shadow Crossbow", AmmoType.HEAVY, 78f, 0.9f, 0.4f, 1.2f, 1, 1.35f, false, 105f, 220f, 2.2f, soundRadius = 24f, autoFire = false),
    ROCKET_LAUNCHER("rpg", "Nova Launcher", AmmoType.ROCKETS, 115f, 0.48f, 1.2f, 4.6f, 1, 2.9f, false, 62f, 260f, 1.0f, splashRadius = 11f, soundRadius = 320f, autoFire = false)
}

enum class ConsumableType(
    val label: String,
    val channelSeconds: Float,
    val hpGain: Float,
    val shieldGain: Float,
    val speedBoostSeconds: Float = 0f,
    val maxCap: Float = 100f
) {
    BANDAGE("Bandage", 2.2f, 25f, 0f, maxCap = 75f),
    MEDKIT("Medkit", 4.5f, 100f, 0f, maxCap = 100f),
    SMALL_SHIELD("Mini Shield", 1.8f, 0f, 25f, maxCap = 50f),
    BIG_SHIELD("Shield Pot", 3.6f, 0f, 50f, maxCap = 100f),
    ENERGY_DRINK("Volt Drink", 2.0f, 15f, 15f, speedBoostSeconds = 12f, maxCap = 100f)
}

enum class GrenadeType(val label: String, val fuseTime: Float, val radius: Float) {
    FRAG("Frag Grenade", 2.3f, 10f),
    SMOKE("Smoke Grenade", 1.5f, 12f),
    FLASH("Flashbang", 1.4f, 14f)
}

data class StormStage(
    val stageNumber: Int,
    val waitSeconds: Float,
    val shrinkSeconds: Float,
    val targetRadius: Float,
    val damagePerSecond: Float
)

object StormConfig {
    // 7 stages tuned for fast, exciting 5-7 minute matches
    val STAGES = listOf(
        StormStage(1, 22f, 34f, 640f, 1f),
        StormStage(2, 18f, 32f, 420f, 2f),
        StormStage(3, 16f, 28f, 260f, 4f),
        StormStage(4, 14f, 26f, 145f, 6f),
        StormStage(5, 12f, 22f, 72f, 9f),
        StormStage(6, 10f, 20f, 28f, 12f),
        StormStage(7, 8f, 18f, 0f, 16f)
    )
}

enum class BotPersonality(
    val label: String,
    val aggression: Float,
    val lootBias: Float,
    val bushBias: Float,
    val airdropBias: Float
) {
    AGGRESSIVE("Aggressive", 0.92f, 0.45f, 0.08f, 0.85f),
    SNEAKY("Sneaky", 0.42f, 0.60f, 0.82f, 0.35f),
    CAMPER("Camper", 0.35f, 0.55f, 0.90f, 0.25f),
    LOOTER("Looter", 0.48f, 0.95f, 0.30f, 0.92f),
    HUNTER("Hunter", 0.85f, 0.55f, 0.25f, 0.78f)
}

enum class RankTier(
    val title: String,
    val minRp: Int,
    val color: Color,
    val botAccuracyScale: Float,
    val botReactionDelay: Float
) {
    BRONZE("Bronze", 0, Color(0xFFCD7F32), 0.42f, 0.65f),
    SILVER("Silver", 600, Color(0xFFC0C9D6), 0.52f, 0.52f),
    GOLD("Gold", 1400, Color(0xFFFFC107), 0.64f, 0.40f),
    PLATINUM("Platinum", 2400, Color(0xFF38BDF8), 0.74f, 0.30f),
    DIAMOND("Diamond", 3600, Color(0xFFA855F7), 0.85f, 0.22f),
    CHAMPION("Champion", 5000, Color(0xFFFF2A85), 0.94f, 0.15f);

    companion object {
        fun fromRp(rp: Int): Pair<RankTier, Int> {
            val tier = entries.lastOrNull { rp >= it.minRp } ?: BRONZE
            val nextMin = entries.getOrNull(tier.ordinal + 1)?.minRp ?: (tier.minRp + 1500)
            val span = (nextMin - tier.minRp).coerceAtLeast(1)
            val progress = ((rp - tier.minRp).coerceAtLeast(0).toFloat() / span.toFloat()).coerceIn(0f, 0.999f)
            // Division III -> II -> I
            val division = 3 - (progress * 3f).toInt().coerceIn(0, 2)
            return tier to division
        }

        fun divisionRoman(div: Int): String = when (div) {
            1 -> "I"
            2 -> "II"
            else -> "III"
        }
    }
}

object BotRoster {
    val NAMES = listOf(
        "KuroVex", "NovaStrike", "GhostHex", "Viper99", "ZeroPulse",
        "ApexByte", "FrostWraith", "RiftWalker", "ShadowLynx", "BlazeKite",
        "EchoSniper", "CobaltFox", "PyroMantis", "DriftKing", "VoltReaper",
        "CyberFang", "TitanWolf", "NeonSpectre", "AstraValk", "RoguePixel",
        "StormCrow", "KestrelX", "SableHawk", "DuneStalker", "MireGhost",
        "HelixOne", "OnyxBlade", "PulseRider", "ZenithBot", "VortexRay",
        "GlitchAce", "CrimsonJax", "OrionBolt", "SierraSix", "IronClaw"
    )
}
