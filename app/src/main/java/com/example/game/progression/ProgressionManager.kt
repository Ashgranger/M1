package com.example.game.progression

import com.example.game.config.RankTier
import com.example.game.config.WeaponClass
import com.example.game.save.ChallengeProgress
import com.example.game.save.PlayerSaveData
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ============================================================================
// SECTION: PROGRESSION MANAGER — XP, Rank, Daily Shop, Pass, Spin & Post-Match
// ============================================================================

data class MatchResultReport(
    val placement: Int,
    val totalPlayers: Int,
    val kills: Int,
    val headshots: Int,
    val damageDealt: Int,
    val shotsFired: Int,
    val shotsHit: Int,
    val survivalSeconds: Int,
    val airdropsLooted: Int,
    val cratesBroken: Int,
    val vehicleDriveSec: Int,
    val distanceMeters: Int,
    val weaponDamageMap: Map<String, Int>,
    val killerName: String?
)

data class PostMatchRewards(
    val report: MatchResultReport,
    val motivationalLine: String,
    val xpEarned: Int,
    val bpXpEarned: Int,
    val coinsEarned: Int,
    val gemsEarned: Int,
    val rpDelta: Int,
    val oldRp: Int,
    val newRp: Int,
    val rankedUp: Boolean,
    val rankedDown: Boolean,
    val leveledUp: Boolean,
    val newLevel: Int,
    val isNewBestPlacement: Boolean,
    val isNewBestKills: Boolean,
    val isNewBestDamage: Boolean,
    val prevBestPlacement: Int,
    val prevBestDamage: Int,
    val newlyUnlockedAchievements: List<AchievementDef>
)

object ProgressionManager {

    fun currentDayKey(): Long = System.currentTimeMillis() / (24L * 60L * 60L * 1000L)
    fun currentWeekKey(): Long = currentDayKey() / 7L

    fun millisUntilDailyReset(): Long {
        val dayMs = 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        return dayMs - (now % dayMs)
    }

    fun xpRequiredForLevel(level: Int): Int = 300 + (level - 1) * 140

    fun battlePassTier(bpXp: Int): Int = (bpXp / ProgressionCatalog.XP_PER_BP_TIER + 1).coerceIn(1, 30)

    fun refreshTimeBasedState(save: PlayerSaveData): PlayerSaveData {
        val today = currentDayKey()
        val week = currentWeekKey()
        var updated = save

        // Update 7-day login streak
        if (updated.lastLoginDayKey != today) {
            val nextDay = if (updated.lastLoginDayKey == today - 1L) {
                (updated.loginStreakDay % 7) + 1
            } else if (updated.lastLoginDayKey == 0L) {
                1
            } else {
                1
            }
            val resetClaimed = if (nextDay == 1 && updated.lastLoginDayKey != 0L) emptyList() else updated.claimedLoginDays
            updated = updated.copy(
                loginStreakDay = nextDay,
                lastLoginDayKey = today,
                claimedLoginDays = resetClaimed
            )
        }

        // Daily challenges (3)
        if (updated.dailyChallengesDayKey != today || updated.dailyChallenges.isEmpty()) {
            updated = updated.copy(
                dailyChallengesDayKey = today,
                dailyChallenges = generateDailyChallenges(today)
            )
        }

        // Weekly challenges (3)
        if (updated.weeklyChallengesWeekKey != week || updated.weeklyChallenges.isEmpty()) {
            updated = updated.copy(
                weeklyChallengesWeekKey = week,
                weeklyChallenges = generateWeeklyChallenges(week)
            )
        }

        if (updated.shopDayKey != today) {
            updated = updated.copy(shopDayKey = today)
        }

        return updated
    }

    fun getDailyShopItems(dayKey: Long): List<CosmeticItem> {
        val purchasable = ProgressionCatalog.COSMETICS.filter { it.priceCoins > 0 || it.priceGems > 0 }
        val rng = Random(dayKey * 31337L + 97L)
        return purchasable.shuffled(rng).take(8)
    }

    private fun generateDailyChallenges(dayKey: Long): List<ChallengeProgress> {
        val pool = listOf(
            ChallengeProgress("d_kills_$dayKey", "Eliminate 5 Opponents", "KILLS", 5, 0, 180, 220, false),
            ChallengeProgress("d_dmg_$dayKey", "Deal 600 Damage", "DAMAGE", 600, 0, 160, 200, false),
            ChallengeProgress("d_match_$dayKey", "Play 2 Matches", "MATCHES", 2, 0, 140, 180, false),
            ChallengeProgress("d_top5_$dayKey", "Finish Top 5 Once", "TOP5", 1, 0, 220, 260, false),
            ChallengeProgress("d_hs_$dayKey", "Score 2 Headshots", "HEADSHOTS", 2, 0, 200, 240, false),
            ChallengeProgress("d_crates_$dayKey", "Break 4 Loot Crates", "CRATES", 4, 0, 150, 180, false)
        )
        return pool.shuffled(Random(dayKey * 997L)).take(3)
    }

    private fun generateWeeklyChallenges(weekKey: Long): List<ChallengeProgress> {
        val pool = listOf(
            ChallengeProgress("w_kills_$weekKey", "Eliminate 25 Opponents", "KILLS", 25, 0, 550, 700, true),
            ChallengeProgress("w_dmg_$weekKey", "Deal 3,500 Damage", "DAMAGE", 3500, 0, 500, 650, true),
            ChallengeProgress("w_top5_$weekKey", "Finish Top 5 Three Times", "TOP5", 3, 0, 600, 750, true),
            ChallengeProgress("w_airdrop_$weekKey", "Loot 3 Supply Airdrops", "AIRDROPS", 3, 0, 550, 700, true),
            ChallengeProgress("w_survive_$weekKey", "Survive 15 Minutes Total", "SURVIVE_MIN", 15, 0, 480, 620, true)
        )
        return pool.shuffled(Random(weekKey * 1337L)).take(3)
    }

    fun applyMatchResult(currentSave: PlayerSaveData, report: MatchResultReport): Pair<PlayerSaveData, PostMatchRewards> {
        val prevStats = currentSave.stats
        val isWin = report.placement == 1
        val newWinStreak = if (isWin) prevStats.winStreak + 1 else 0

        val updatedStats = prevStats.copy(
            matchesPlayed = prevStats.matchesPlayed + 1,
            wins = prevStats.wins + if (isWin) 1 else 0,
            totalKills = prevStats.totalKills + report.kills,
            totalDeaths = prevStats.totalDeaths + if (isWin) 0 else 1,
            totalDamage = prevStats.totalDamage + report.damageDealt,
            totalHeadshots = prevStats.totalHeadshots + report.headshots,
            shotsFired = prevStats.shotsFired + report.shotsFired,
            shotsHit = prevStats.shotsHit + report.shotsHit,
            bestPlacement = min(prevStats.bestPlacement, report.placement),
            bestKillsInMatch = max(prevStats.bestKillsInMatch, report.kills),
            bestDamageInMatch = max(prevStats.bestDamageInMatch, report.damageDealt),
            longestSurvivalSec = max(prevStats.longestSurvivalSec, report.survivalSeconds),
            airdropsLooted = prevStats.airdropsLooted + report.airdropsLooted,
            cratesBroken = prevStats.cratesBroken + report.cratesBroken,
            vehiclesDrivenSec = prevStats.vehiclesDrivenSec + report.vehicleDriveSec,
            distanceTraveledMeters = prevStats.distanceTraveledMeters + report.distanceMeters,
            winStreak = newWinStreak,
            bestWinStreak = max(prevStats.bestWinStreak, newWinStreak)
        )

        // Rewards calculation
        val placementBonus = when {
            report.placement == 1 -> 260
            report.placement <= 3 -> 170
            report.placement <= 5 -> 125
            report.placement <= 10 -> 80
            else -> 35
        }
        val xpEarned = placementBonus + report.kills * 45 + (report.damageDealt / 12) + (report.survivalSeconds / 4)
        val bpXpEarned = (xpEarned * 0.85f).toInt()
        val coinsEarned = (placementBonus / 2) + report.kills * 28 + (report.damageDealt / 25)
        val gemsEarned = if (isWin) 15 else if (report.placement <= 3) 5 else 0

        // Rank RP delta (can rank up or rank down at higher ranks)
        val (oldTier, oldDiv) = RankTier.fromRp(currentSave.rp)
        val entryCost = oldTier.ordinal * 14
        val placementRp = when {
            report.placement == 1 -> 130
            report.placement == 2 -> 92
            report.placement == 3 -> 74
            report.placement <= 5 -> 55
            report.placement <= 10 -> 30
            report.placement <= 18 -> 12
            else -> 0
        }
        val rpDelta = placementRp + report.kills * 16 - entryCost
        val newRp = (currentSave.rp + rpDelta).coerceAtLeast(0)
        val (newTier, newDiv) = RankTier.fromRp(newRp)
        val rankedUp = newTier.ordinal > oldTier.ordinal || (newTier == oldTier && newDiv < oldDiv)
        val rankedDown = newTier.ordinal < oldTier.ordinal || (newTier == oldTier && newDiv > oldDiv)

        // Level up math
        var lvl = currentSave.level
        var xpPool = currentSave.xp + xpEarned
        var leveledUp = false
        var levelUpCoins = 0
        var levelUpGems = 0
        while (xpPool >= xpRequiredForLevel(lvl)) {
            xpPool -= xpRequiredForLevel(lvl)
            lvl++
            leveledUp = true
            levelUpCoins += 150
            levelUpGems += 10
        }

        // Update weapon mastery XP
        val updatedMastery = currentSave.weaponMasteryXp.toMutableMap()
        report.weaponDamageMap.forEach { (weaponId, dmg) ->
            val gain = (dmg / 3).coerceAtLeast(10)
            updatedMastery[weaponId] = (updatedMastery[weaponId] ?: 0) + gain
        }

        // Update daily & weekly challenges
        fun updateList(list: List<ChallengeProgress>): List<ChallengeProgress> = list.map { ch ->
            val add = when (ch.metric) {
                "KILLS" -> report.kills
                "DAMAGE" -> report.damageDealt
                "MATCHES" -> 1
                "TOP5" -> if (report.placement <= 5) 1 else 0
                "HEADSHOTS" -> report.headshots
                "CRATES" -> report.cratesBroken
                "AIRDROPS" -> report.airdropsLooted
                "SURVIVE_MIN" -> (report.survivalSeconds / 60).coerceAtLeast(1)
                else -> 0
            }
            ch.copy(current = (ch.current + add).coerceAtMost(ch.target))
        }

        // Unlock achievements
        val alreadyUnlocked = currentSave.unlockedAchievements.toMutableSet()
        val newlyUnlocked = mutableListOf<AchievementDef>()
        for (ach in ProgressionCatalog.ACHIEVEMENTS) {
            if (ach.id !in alreadyUnlocked) {
                val value = ach.progressProvider(updatedStats, lvl, newRp)
                if (value >= ach.targetValue) {
                    alreadyUnlocked.add(ach.id)
                    newlyUnlocked.add(ach)
                }
            }
        }

        val motivational = when {
            report.placement == 1 -> "VICTORY ROYALE! You dominated Zone Zero!"
            report.placement == 2 -> "You finished 2nd. One fight away from the crown!"
            report.placement == 3 -> "You finished 3rd. So close!"
            report.placement <= 5 -> "You finished #${report.placement}. Top 5 finish — keep pushing!"
            report.kills >= 5 -> "You finished #${report.placement} with ${report.kills} eliminations. Fierce run!"
            else -> "You finished #${report.placement}. Drop hot and run it back!"
        }

        val nextSave = currentSave.copy(
            level = lvl,
            xp = xpPool,
            rp = newRp,
            coins = currentSave.coins + coinsEarned + levelUpCoins,
            gems = currentSave.gems + gemsEarned + levelUpGems,
            battlePassXp = currentSave.battlePassXp + bpXpEarned,
            dailyChallenges = updateList(currentSave.dailyChallenges),
            weeklyChallenges = updateList(currentSave.weeklyChallenges),
            unlockedAchievements = alreadyUnlocked.toList(),
            weaponMasteryXp = updatedMastery,
            stats = updatedStats
        )

        val rewards = PostMatchRewards(
            report = report,
            motivationalLine = motivational,
            xpEarned = xpEarned,
            bpXpEarned = bpXpEarned,
            coinsEarned = coinsEarned + levelUpCoins,
            gemsEarned = gemsEarned + levelUpGems,
            rpDelta = rpDelta,
            oldRp = currentSave.rp,
            newRp = newRp,
            rankedUp = rankedUp,
            rankedDown = rankedDown,
            leveledUp = leveledUp,
            newLevel = lvl,
            isNewBestPlacement = report.placement < prevStats.bestPlacement || prevStats.matchesPlayed == 0,
            isNewBestKills = report.kills > prevStats.bestKillsInMatch && report.kills > 0,
            isNewBestDamage = report.damageDealt > prevStats.bestDamageInMatch && report.damageDealt > 0,
            prevBestPlacement = if (prevStats.matchesPlayed == 0) report.placement else prevStats.bestPlacement,
            prevBestDamage = prevStats.bestDamageInMatch,
            newlyUnlockedAchievements = newlyUnlocked
        )

        return nextSave to rewards
    }

    fun rollLuckySpin(rng: Random = Random.Default): LuckySpinSlot {
        val totalWeight = ProgressionCatalog.LUCKY_SPIN_SLOTS.sumOf { it.weight }
        var roll = rng.nextInt(totalWeight)
        for (slot in ProgressionCatalog.LUCKY_SPIN_SLOTS) {
            roll -= slot.weight
            if (roll < 0) return slot
        }
        return ProgressionCatalog.LUCKY_SPIN_SLOTS.first()
    }

    fun canFreeSpin(save: PlayerSaveData): Boolean =
        System.currentTimeMillis() - save.lastLuckySpinMs >= ProgressionCatalog.LUCKY_SPIN_COOLDOWN_MS

    fun millisUntilFreeSpin(save: PlayerSaveData): Long =
        (ProgressionCatalog.LUCKY_SPIN_COOLDOWN_MS - (System.currentTimeMillis() - save.lastLuckySpinMs)).coerceAtLeast(0L)

    fun weaponMasteryList(save: PlayerSaveData): List<Triple<WeaponClass, Int, Float>> =
        WeaponClass.entries.map { wc ->
            val xp = save.weaponMasteryXp[wc.id] ?: 0
            Triple(wc, ProgressionCatalog.masteryLevelForXp(xp), ProgressionCatalog.masteryProgressInLevel(xp))
        }
}
