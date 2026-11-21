package com.example.game.progression

import com.example.game.config.Rarity
import com.example.game.config.WeaponClass
import com.example.game.save.CareerStats

// ============================================================================
// SECTION: PROGRESSION CATALOG — Cosmetics, 30-Tier Pass, 32 Achievements, Spin
// ============================================================================

enum class CosmeticCategory(val label: String) {
    CHARACTER_SKIN("Operative"),
    WEAPON_SKIN("Weapon Wrap"),
    BULLET_TRAIL("Bullet Trail"),
    PARACHUTE("Parachute"),
    LANDING_EFFECT("Drop Effect"),
    KILL_EFFECT("Kill Effect"),
    EMOTE("Emote")
}

data class CosmeticItem(
    val id: String,
    val name: String,
    val category: CosmeticCategory,
    val rarity: Rarity,
    val priceCoins: Int = 0,
    val priceGems: Int = 0,
    val primaryRgb: FloatArray,
    val accentRgb: FloatArray,
    val description: String = ""
)

data class BattlePassTier(
    val tier: Int,
    val freeLabel: String,
    val freeCoins: Int = 0,
    val freeGems: Int = 0,
    val freeCosmeticId: String? = null,
    val premiumLabel: String,
    val premiumCoins: Int = 0,
    val premiumGems: Int = 0,
    val premiumCosmeticId: String? = null
)

data class LoginReward(
    val day: Int,
    val label: String,
    val coins: Int = 0,
    val gems: Int = 0,
    val cosmeticId: String? = null
)

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val rewardCoins: Int,
    val rewardGems: Int,
    val targetValue: Int,
    val progressProvider: (CareerStats, Int, Int) -> Int // stats, level, rp
)

data class LuckySpinSlot(
    val id: String,
    val label: String,
    val rarity: Rarity,
    val coins: Int = 0,
    val gems: Int = 0,
    val cosmeticId: String? = null,
    val weight: Int = 10
)

object ProgressionCatalog {
    const val PREMIUM_PASS_GEMS = 250
    const val XP_PER_BP_TIER = 400
    const val LUCKY_SPIN_COOLDOWN_MS = 4L * 60L * 60L * 1000L

    val COSMETICS: List<CosmeticItem> = listOf(
        // Character Skins
        CosmeticItem("skin_cyber_scout", "Cyber Scout", CosmeticCategory.CHARACTER_SKIN, Rarity.COMMON, 0, 0, floatArrayOf(0.16f, 0.24f, 0.36f), floatArrayOf(0.0f, 0.94f, 1.0f), "Standard issue tactical exo-suit."),
        CosmeticItem("skin_dune_viper", "Dune Viper", CosmeticCategory.CHARACTER_SKIN, Rarity.UNCOMMON, 450, 0, floatArrayOf(0.66f, 0.54f, 0.34f), floatArrayOf(0.95f, 0.65f, 0.12f), "Desert recon camo plating."),
        CosmeticItem("skin_frost_wraith", "Frost Wraith", CosmeticCategory.CHARACTER_SKIN, Rarity.RARE, 850, 0, floatArrayOf(0.82f, 0.90f, 0.97f), floatArrayOf(0.22f, 0.74f, 0.97f), "Arctic stealth thermal armor."),
        CosmeticItem("skin_crimson_ronin", "Crimson Ronin", CosmeticCategory.CHARACTER_SKIN, Rarity.EPIC, 0, 85, floatArrayOf(0.55f, 0.08f, 0.16f), floatArrayOf(1.0f, 0.16f, 0.52f), "Neon-edged samurai combat rig."),
        CosmeticItem("skin_storm_warden", "Storm Warden", CosmeticCategory.CHARACTER_SKIN, Rarity.EPIC, 1400, 0, floatArrayOf(0.22f, 0.12f, 0.38f), floatArrayOf(0.66f, 0.33f, 0.97f), "Storm-forged containment harness."),
        CosmeticItem("skin_solar_apex", "Solar Apex", CosmeticCategory.CHARACTER_SKIN, Rarity.LEGENDARY, 0, 160, floatArrayOf(0.14f, 0.13f, 0.18f), floatArrayOf(1.0f, 0.72f, 0.0f), "Gilded champion battle armor."),
        CosmeticItem("skin_toxic_mire", "Mire Stalker", CosmeticCategory.CHARACTER_SKIN, Rarity.RARE, 750, 0, floatArrayOf(0.15f, 0.32f, 0.20f), floatArrayOf(0.13f, 0.85f, 0.45f), "Swamp ambush ghille chassis."),
        CosmeticItem("skin_phantom_zero", "Phantom Zero", CosmeticCategory.CHARACTER_SKIN, Rarity.LEGENDARY, 0, 180, floatArrayOf(0.06f, 0.08f, 0.14f), floatArrayOf(0.0f, 1.0f, 0.88f), "Experimental stealth carbon prototype."),

        // Weapon Skins
        CosmeticItem("wskin_carbon", "Carbon Matte", CosmeticCategory.WEAPON_SKIN, Rarity.COMMON, 0, 0, floatArrayOf(0.18f, 0.20f, 0.24f), floatArrayOf(0.45f, 0.50f, 0.58f)),
        CosmeticItem("wskin_neon_pulse", "Neon Pulse", CosmeticCategory.WEAPON_SKIN, Rarity.RARE, 500, 0, floatArrayOf(0.08f, 0.16f, 0.28f), floatArrayOf(0.0f, 0.94f, 1.0f)),
        CosmeticItem("wskin_plasma_rose", "Plasma Rose", CosmeticCategory.WEAPON_SKIN, Rarity.EPIC, 0, 60, floatArrayOf(0.28f, 0.08f, 0.22f), floatArrayOf(1.0f, 0.16f, 0.52f)),
        CosmeticItem("wskin_auric_gold", "Auric Gold", CosmeticCategory.WEAPON_SKIN, Rarity.LEGENDARY, 0, 110, floatArrayOf(0.88f, 0.68f, 0.14f), floatArrayOf(1.0f, 0.92f, 0.52f)),
        CosmeticItem("wskin_emerald_volt", "Emerald Volt", CosmeticCategory.WEAPON_SKIN, Rarity.UNCOMMON, 350, 0, floatArrayOf(0.10f, 0.28f, 0.18f), floatArrayOf(0.13f, 0.90f, 0.42f)),

        // Bullet Trails
        CosmeticItem("trail_cyan", "Cyan Tracer", CosmeticCategory.BULLET_TRAIL, Rarity.COMMON, 0, 0, floatArrayOf(0.0f, 0.94f, 1.0f), floatArrayOf(0.7f, 1.0f, 1.0f)),
        CosmeticItem("trail_magenta", "Plasma Beam", CosmeticCategory.BULLET_TRAIL, Rarity.RARE, 480, 0, floatArrayOf(1.0f, 0.16f, 0.52f), floatArrayOf(1.0f, 0.65f, 0.82f)),
        CosmeticItem("trail_gold", "Solar Streak", CosmeticCategory.BULLET_TRAIL, Rarity.LEGENDARY, 0, 95, floatArrayOf(1.0f, 0.75f, 0.05f), floatArrayOf(1.0f, 0.96f, 0.65f)),
        CosmeticItem("trail_violet", "Storm Arc", CosmeticCategory.BULLET_TRAIL, Rarity.EPIC, 800, 0, floatArrayOf(0.62f, 0.30f, 0.95f), floatArrayOf(0.85f, 0.68f, 1.0f)),

        // Parachutes
        CosmeticItem("chute_standard", "Tactical Canopy", CosmeticCategory.PARACHUTE, Rarity.COMMON, 0, 0, floatArrayOf(0.22f, 0.30f, 0.42f), floatArrayOf(0.0f, 0.94f, 1.0f)),
        CosmeticItem("chute_cyber_wing", "Cyber Wing", CosmeticCategory.PARACHUTE, Rarity.RARE, 600, 0, floatArrayOf(0.05f, 0.65f, 0.88f), floatArrayOf(0.0f, 1.0f, 0.85f)),
        CosmeticItem("chute_nova_flame", "Nova Flare", CosmeticCategory.PARACHUTE, Rarity.EPIC, 0, 75, floatArrayOf(0.95f, 0.22f, 0.45f), floatArrayOf(1.0f, 0.72f, 0.0f)),
        CosmeticItem("chute_void_crown", "Void Crown", CosmeticCategory.PARACHUTE, Rarity.LEGENDARY, 0, 120, floatArrayOf(0.32f, 0.10f, 0.58f), floatArrayOf(1.0f, 0.78f, 0.10f)),

        // Landing Effects
        CosmeticItem("land_dust", "Impact Dust", CosmeticCategory.LANDING_EFFECT, Rarity.COMMON, 0, 0, floatArrayOf(0.75f, 0.72f, 0.66f), floatArrayOf(0.9f, 0.9f, 0.9f)),
        CosmeticItem("land_shockwave", "Cyan Shockwave", CosmeticCategory.LANDING_EFFECT, Rarity.RARE, 550, 0, floatArrayOf(0.0f, 0.94f, 1.0f), floatArrayOf(0.35f, 0.98f, 1.0f)),
        CosmeticItem("land_nova", "Amber Nova", CosmeticCategory.LANDING_EFFECT, Rarity.EPIC, 0, 70, floatArrayOf(1.0f, 0.62f, 0.04f), floatArrayOf(1.0f, 0.28f, 0.48f)),

        // Kill Effects
        CosmeticItem("kill_sparks", "Kinetic Sparks", CosmeticCategory.KILL_EFFECT, Rarity.COMMON, 0, 0, floatArrayOf(1.0f, 0.80f, 0.25f), floatArrayOf(1.0f, 0.45f, 0.15f)),
        CosmeticItem("kill_glitch", "Holo Shatter", CosmeticCategory.KILL_EFFECT, Rarity.EPIC, 950, 0, floatArrayOf(0.0f, 0.94f, 1.0f), floatArrayOf(1.0f, 0.16f, 0.52f)),
        CosmeticItem("kill_vortex", "Void Implosion", CosmeticCategory.KILL_EFFECT, Rarity.LEGENDARY, 0, 130, floatArrayOf(0.62f, 0.30f, 0.95f), floatArrayOf(1.0f, 0.75f, 0.05f)),

        // Emotes
        CosmeticItem("emote_salute", "Tactical Salute", CosmeticCategory.EMOTE, Rarity.COMMON, 0, 0, floatArrayOf(0.0f, 0.94f, 1.0f), floatArrayOf(0.9f, 0.95f, 1.0f)),
        CosmeticItem("emote_hype", "Cyber Hype", CosmeticCategory.EMOTE, Rarity.RARE, 400, 0, floatArrayOf(1.0f, 0.16f, 0.52f), floatArrayOf(0.0f, 0.94f, 1.0f)),
        CosmeticItem("emote_crown", "Champion Crown", CosmeticCategory.EMOTE, Rarity.LEGENDARY, 0, 90, floatArrayOf(1.0f, 0.75f, 0.05f), floatArrayOf(1.0f, 0.95f, 0.55f))
    )

    val COSMETIC_BY_ID: Map<String, CosmeticItem> = COSMETICS.associateBy { it.id }

    val BATTLE_PASS_TIERS: List<BattlePassTier> = (1..30).map { tier ->
        when (tier) {
            1 -> BattlePassTier(1, "150 Coins", freeCoins = 150, premiumLabel = "Neon Pulse Wrap", premiumCosmeticId = "wskin_neon_pulse")
            5 -> BattlePassTier(5, "250 Coins", freeCoins = 250, premiumLabel = "Cyber Wing Chute", premiumCosmeticId = "chute_cyber_wing")
            10 -> BattlePassTier(10, "Dune Viper Skin", freeCosmeticId = "skin_dune_viper", premiumLabel = "Plasma Beam Trail", premiumCosmeticId = "trail_magenta")
            15 -> BattlePassTier(15, "40 Gems", freeGems = 40, premiumLabel = "Crimson Ronin", premiumCosmeticId = "skin_crimson_ronin")
            20 -> BattlePassTier(20, "Cyan Shockwave", freeCosmeticId = "land_shockwave", premiumLabel = "Holo Shatter FX", premiumCosmeticId = "kill_glitch")
            25 -> BattlePassTier(25, "60 Gems", freeGems = 60, premiumLabel = "Auric Gold Wrap", premiumCosmeticId = "wskin_auric_gold")
            30 -> BattlePassTier(30, "Storm Warden", freeCosmeticId = "skin_storm_warden", premiumLabel = "Phantom Zero", premiumCosmeticId = "skin_phantom_zero")
            else -> {
                val freeGems = if (tier % 6 == 0) 20 else 0
                val freeCoins = if (freeGems == 0) 120 + tier * 15 else 0
                val premGems = if (tier % 3 == 0) 30 else 0
                val premCoins = if (premGems == 0) 220 + tier * 20 else 0
                BattlePassTier(
                    tier = tier,
                    freeLabel = if (freeGems > 0) "$freeGems Gems" else "$freeCoins Coins",
                    freeCoins = freeCoins,
                    freeGems = freeGems,
                    premiumLabel = if (premGems > 0) "$premGems Gems" else "$premCoins Coins",
                    premiumCoins = premCoins,
                    premiumGems = premGems
                )
            }
        }
    }

    val LOGIN_STREAK: List<LoginReward> = listOf(
        LoginReward(1, "200 Coins", coins = 200),
        LoginReward(2, "25 Gems", gems = 25),
        LoginReward(3, "350 Coins", coins = 350),
        LoginReward(4, "Emerald Volt Wrap", cosmeticId = "wskin_emerald_volt"),
        LoginReward(5, "500 Coins", coins = 500),
        LoginReward(6, "50 Gems", gems = 50),
        LoginReward(7, "Frost Wraith Skin", cosmeticId = "skin_frost_wraith", gems = 30)
    )

    val LUCKY_SPIN_SLOTS: List<LuckySpinSlot> = listOf(
        LuckySpinSlot("spin_c1", "150 Coins", Rarity.COMMON, coins = 150, weight = 26),
        LuckySpinSlot("spin_c2", "300 Coins", Rarity.UNCOMMON, coins = 300, weight = 20),
        LuckySpinSlot("spin_g1", "20 Gems", Rarity.RARE, gems = 20, weight = 16),
        LuckySpinSlot("spin_c3", "600 Coins", Rarity.RARE, coins = 600, weight = 12),
        LuckySpinSlot("spin_g2", "45 Gems", Rarity.EPIC, gems = 45, weight = 10),
        LuckySpinSlot("spin_fx", "Storm Arc Trail", Rarity.EPIC, cosmeticId = "trail_violet", coins = 350, weight = 8),
        LuckySpinSlot("spin_g3", "90 Gems", Rarity.LEGENDARY, gems = 90, weight = 5),
        LuckySpinSlot("spin_leg", "Solar Apex Skin", Rarity.LEGENDARY, cosmeticId = "skin_solar_apex", gems = 100, weight = 3)
    )

    val ACHIEVEMENTS: List<AchievementDef> = listOf(
        AchievementDef("ach_first_drop", "First Drop", "Complete your first match", 150, 10, 1) { s, _, _ -> s.matchesPlayed },
        AchievementDef("ach_veteran_10", "Island Regular", "Play 10 matches", 300, 15, 10) { s, _, _ -> s.matchesPlayed },
        AchievementDef("ach_veteran_50", "Zone Veteran", "Play 50 matches", 750, 35, 50) { s, _, _ -> s.matchesPlayed },
        AchievementDef("ach_first_blood", "First Blood", "Score your first elimination", 150, 10, 1) { s, _, _ -> s.totalKills },
        AchievementDef("ach_slayer_10", "Tactical Slayer", "Eliminate 10 opponents", 250, 15, 10) { s, _, _ -> s.totalKills },
        AchievementDef("ach_slayer_50", "Apex Predator", "Eliminate 50 opponents", 500, 25, 50) { s, _, _ -> s.totalKills },
        AchievementDef("ach_slayer_150", "Reaper of Zero", "Eliminate 150 opponents", 1000, 50, 150) { s, _, _ -> s.totalKills },
        AchievementDef("ach_slayer_500", "Island Legend", "Eliminate 500 opponents", 2000, 100, 500) { s, _, _ -> s.totalKills },
        AchievementDef("ach_win_1", "Zone Zero Champion", "Win your first Victory Royale", 400, 30, 1) { s, _, _ -> s.wins },
        AchievementDef("ach_win_5", "Five-Time Crown", "Win 5 matches", 750, 45, 5) { s, _, _ -> s.wins },
        AchievementDef("ach_win_15", "Dynasty", "Win 15 matches", 1500, 80, 15) { s, _, _ -> s.wins },
        AchievementDef("ach_streak_2", "Back-to-Back", "Win 2 matches in a row", 500, 30, 2) { s, _, _ -> s.bestWinStreak },
        AchievementDef("ach_streak_3", "Unstoppable Dynasty", "Win 3 matches in a row", 1000, 60, 3) { s, _, _ -> s.bestWinStreak },
        AchievementDef("ach_headshot_5", "Sharpshooter", "Land 5 headshot kills", 250, 15, 5) { s, _, _ -> s.totalHeadshots },
        AchievementDef("ach_headshot_25", "Deadeye", "Land 25 headshot kills", 600, 35, 25) { s, _, _ -> s.totalHeadshots },
        AchievementDef("ach_headshot_100", "Phantom Marksman", "Land 100 headshot kills", 1200, 70, 100) { s, _, _ -> s.totalHeadshots },
        AchievementDef("ach_dmg_1000", "Heavy Fire", "Deal 1,000 total damage", 200, 10, 1000) { s, _, _ -> s.totalDamage },
        AchievementDef("ach_dmg_10000", "Demolitionist", "Deal 10,000 total damage", 500, 25, 10000) { s, _, _ -> s.totalDamage },
        AchievementDef("ach_dmg_50000", "War Machine", "Deal 50,000 total damage", 1200, 60, 50000) { s, _, _ -> s.totalDamage },
        AchievementDef("ach_kills_match_5", "Rampage Operative", "Get 5 kills in one match", 350, 20, 5) { s, _, _ -> s.bestKillsInMatch },
        AchievementDef("ach_kills_match_10", "Lobby Wipe", "Get 10 kills in one match", 800, 45, 10) { s, _, _ -> s.bestKillsInMatch },
        AchievementDef("ach_top3", "So Close", "Finish Top 3 in a match", 250, 15, 1) { s, _, _ -> if (s.bestPlacement <= 3) 1 else 0 },
        AchievementDef("ach_survive_3m", "Storm Runner", "Survive 3 minutes in a match", 200, 10, 180) { s, _, _ -> s.longestSurvivalSec },
        AchievementDef("ach_survive_5m", "Endgame Survivor", "Survive 5 minutes in a match", 400, 20, 300) { s, _, _ -> s.longestSurvivalSec },
        AchievementDef("ach_airdrop_1", "Supply Hunter", "Open 1 Airdrop crate", 250, 15, 1) { s, _, _ -> s.airdropsLooted },
        AchievementDef("ach_airdrop_10", "Airdrop Baron", "Open 10 Airdrop crates", 700, 40, 10) { s, _, _ -> s.airdropsLooted },
        AchievementDef("ach_crates_10", "Salvager", "Break 10 wooden crates", 200, 10, 10) { s, _, _ -> s.cratesBroken },
        AchievementDef("ach_crates_50", "Demolition Crew", "Break 50 wooden crates", 500, 25, 50) { s, _, _ -> s.cratesBroken },
        AchievementDef("ach_drive_60", "Road Warrior", "Drive vehicles for 60s", 250, 15, 60) { s, _, _ -> s.vehiclesDrivenSec },
        AchievementDef("ach_rank_silver", "Silver Operative", "Reach Silver Rank (600 RP)", 350, 20, 600) { _, _, rp -> rp },
        AchievementDef("ach_rank_gold", "Gold Tactician", "Reach Gold Rank (1400 RP)", 600, 35, 1400) { _, _, rp -> rp },
        AchievementDef("ach_rank_diamond", "Diamond Elite", "Reach Diamond Rank (3600 RP)", 1200, 75, 3600) { _, _, rp -> rp },
        AchievementDef("ach_level_10", "Seasoned Commander", "Reach Account Level 10", 500, 30, 10) { _, lvl, _ -> lvl }
    )

    fun masteryLevelForXp(xp: Int): Int = (xp / 250) + 1
    fun masteryProgressInLevel(xp: Int): Float = (xp % 250) / 250f
    fun masteryRewardLabel(weapon: WeaponClass, level: Int): String = when {
        level >= 5 -> "${weapon.displayName} Gold Badge + 40 Gems"
        level >= 3 -> "${weapon.displayName} Neon Emblem + 300 Coins"
        else -> "${weapon.displayName} Bronze Mastery + 150 Coins"
    }
}
