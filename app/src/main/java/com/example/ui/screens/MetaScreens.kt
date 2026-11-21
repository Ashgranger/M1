package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.config.RankTier
import com.example.game.config.WeaponClass
import com.example.game.progression.AchievementDef
import com.example.game.progression.CosmeticItem
import com.example.game.progression.LuckySpinSlot
import com.example.game.progression.ProgressionCatalog
import com.example.game.progression.ProgressionManager
import com.example.game.save.PlayerSaveData
import com.example.ui.theme.BioEmerald
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PlasmaMagenta
import com.example.ui.theme.SlateArmor
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.StormViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MetaScreenHeader(
    title: String,
    subtitle: String,
    save: PlayerSaveData,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("meta_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrencyPill("${save.coins}", LegendaryAmber, "COINS")
            CurrencyPill("${save.gems}", NeonCyan, "GEMS")
        }
    }
}

// ============================================================================
// 1. DAILY ROTATING SHOP + FULL COSMETICS LOCKER
// ============================================================================

@Composable
fun ShopScreen(
    save: PlayerSaveData,
    onBack: () -> Unit,
    onBuyOrEquip: (CosmeticItem) -> Unit
) {
    var showLocker by remember { mutableStateOf(false) }
    var countdownText by remember { mutableStateOf("23:59:59") }

    LaunchedEffect(Unit) {
        while (true) {
            val ms = ProgressionManager.millisUntilDailyReset()
            val h = ms / 3_600_000L
            val m = (ms % 3_600_000L) / 60_000L
            val s = (ms % 60_000L) / 1000L
            countdownText = "%02d:%02d:%02d".format(h, m, s)
            delay(1000L)
        }
    }

    val itemsToShow = remember(save.shopDayKey, showLocker, save.ownedCosmetics) {
        if (showLocker) ProgressionCatalog.COSMETICS.filter { it.id in save.ownedCosmetics }
        else ProgressionManager.getDailyShopItems(save.shopDayKey)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
    ) {
        MetaScreenHeader(
            title = if (showLocker) "LOCKER" else "DAILY SHOP",
            subtitle = if (showLocker) "${itemsToShow.size} owned cosmetics ready to equip" else "Rotates in $countdownText",
            save = save,
            onBack = onBack
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            ToggleChip("DAILY ROTATION", !showLocker) { showLocker = false }
            ToggleChip("MY LOCKER (${save.ownedCosmetics.size})", showLocker) { showLocker = true }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(175.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(itemsToShow) { item ->
                val owned = item.id in save.ownedCosmetics
                val equipped = item.id in listOf(
                    save.equippedCharacterSkin, save.equippedWeaponSkin, save.equippedBulletTrail,
                    save.equippedParachute, save.equippedLandingEffect, save.equippedKillEffect, save.equippedEmote
                )
                Surface(
                    color = SlateArmor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, if (equipped) NeonCyan else item.rarity.color.copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(item.category.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(item.rarity.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = item.rarity.color)
                        }
                        // Color Swatch Preview
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(item.primaryRgb[0], item.primaryRgb[1], item.primaryRgb[2]))
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(item.accentRgb[0], item.accentRgb[1], item.accentRgb[2]))
                            )
                            Text(item.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1)
                        }
                        Button(
                            onClick = { onBuyOrEquip(item) },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    equipped -> BioEmerald.copy(alpha = 0.25f)
                                    owned -> NeonCyan.copy(alpha = 0.22f)
                                    item.priceGems > 0 -> NeonCyan
                                    else -> LegendaryAmber
                                },
                                contentColor = if (owned) TextPrimary else VoidObsidian
                            )
                        ) {
                            val txt = when {
                                equipped -> "EQUIPPED"
                                owned -> "EQUIP"
                                item.priceGems > 0 -> "${item.priceGems} GEMS"
                                else -> "${item.priceCoins} COINS"
                            }
                            Text(txt, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. 30-TIER BATTLE PASS (FREE + PREMIUM TRACKS)
// ============================================================================

@Composable
fun BattlePassScreen(
    save: PlayerSaveData,
    onBack: () -> Unit,
    onUnlockPremium: () -> Unit,
    onClaimTier: (Int, Boolean) -> Unit
) {
    val currentTier = ProgressionManager.battlePassTier(save.battlePassXp)
    val tierProgress = (save.battlePassXp % ProgressionCatalog.XP_PER_BP_TIER).toFloat() / ProgressionCatalog.XP_PER_BP_TIER

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
    ) {
        MetaScreenHeader(
            title = "SEASON 1 BATTLE PASS",
            subtitle = "Tier $currentTier / 30  ·  Earn XP every match & challenge",
            save = save,
            onBack = onBack
        )

        // Banner Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_battlepass_banner_1795236065304),
                contentDescription = "Battle Pass Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(VoidObsidian.copy(alpha = 0.58f)))
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TIER $currentTier / 30", style = MaterialTheme.typography.headlineMedium, color = LegendaryAmber)
                    LinearProgressIndicator(
                        progress = { tierProgress },
                        modifier = Modifier.fillMaxWidth(0.65f).height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = NeonCyan,
                        trackColor = SlateBorder
                    )
                }
                if (!save.battlePassPremium) {
                    Button(
                        onClick = onUnlockPremium,
                        colors = ButtonDefaults.buttonColors(containerColor = PlasmaMagenta, contentColor = TextPrimary),
                        modifier = Modifier.testTag("unlock_premium_pass_button")
                    ) {
                        Text("UNLOCK PREMIUM (${ProgressionCatalog.PREMIUM_PASS_GEMS} GEMS)", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Surface(color = BioEmerald.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BioEmerald)) {
                        Text("PREMIUM ACTIVE", style = MaterialTheme.typography.labelLarge, color = BioEmerald, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(ProgressionCatalog.BATTLE_PASS_TIERS) { def ->
                val unlocked = def.tier <= currentTier
                val freeClaimed = def.tier in save.claimedFreeBpTiers
                val premClaimed = def.tier in save.claimedPremiumBpTiers

                Surface(
                    color = SlateArmor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (unlocked) NeonCyan else SlateBorder),
                    modifier = Modifier.width(156.dp).fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TIER ${def.tier}", style = MaterialTheme.typography.labelLarge, color = if (unlocked) NeonCyan else TextSecondary)

                        // Free Track Box
                        PassTrackCard(
                            track = "FREE",
                            label = def.freeLabel,
                            unlocked = unlocked,
                            claimed = freeClaimed,
                            accent = NeonCyan,
                            onClick = { onClaimTier(def.tier, false) }
                        )

                        // Premium Track Box
                        PassTrackCard(
                            track = "PREMIUM",
                            label = def.premiumLabel,
                            unlocked = unlocked && save.battlePassPremium,
                            claimed = premClaimed,
                            accent = LegendaryAmber,
                            onClick = { onClaimTier(def.tier, true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassTrackCard(
    track: String,
    label: String,
    unlocked: Boolean,
    claimed: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        color = SlateElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (unlocked && !claimed) accent else SlateBorder),
        modifier = Modifier.fillMaxWidth().clickable(enabled = unlocked && !claimed, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(track, style = MaterialTheme.typography.labelSmall, color = accent)
                if (claimed) Icon(Icons.Default.CheckCircle, contentDescription = "Claimed", tint = BioEmerald, modifier = Modifier.size(14.dp))
                else if (!unlocked) Icon(Icons.Default.Lock, contentDescription = "Locked", tint = TextSecondary, modifier = Modifier.size(14.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1)
            Text(
                text = when {
                    claimed -> "CLAIMED"
                    unlocked -> "TAP TO CLAIM"
                    else -> "LOCKED"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (unlocked && !claimed) accent else TextSecondary
            )
        }
    }
}

// ============================================================================
// 3. CHALLENGES (3 DAILY + 3 WEEKLY), 7-DAY STREAK & 33 ACHIEVEMENTS
// ============================================================================

@Composable
fun ChallengesScreen(
    save: PlayerSaveData,
    onBack: () -> Unit,
    onClaimChallenge: (String, Boolean) -> Unit,
    onClaimLoginDay: (Int) -> Unit,
    onClaimAchievement: (AchievementDef) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) } // 0 = Quests, 1 = 7-Day Streak, 2 = Achievements

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
    ) {
        MetaScreenHeader("MISSIONS & REWARDS", "Daily quests, 7-day streak & 33 career achievements", save, onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            ToggleChip("DAILY & WEEKLY (6)", tab == 0) { tab = 0 }
            ToggleChip("7-DAY STREAK", tab == 1) { tab = 1 }
            ToggleChip("ACHIEVEMENTS (${save.unlockedAchievements.size}/${ProgressionCatalog.ACHIEVEMENTS.size})", tab == 2) { tab = 2 }
        }

        when (tab) {
            0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                item { Text("DAILY CHALLENGES", style = MaterialTheme.typography.labelLarge, color = NeonCyan) }
                items(save.dailyChallenges) { ch ->
                    ChallengeRow(ch.title, ch.current, ch.target, "${ch.rewardCoins}c + ${ch.rewardXp} XP", ch.claimed) {
                        onClaimChallenge(ch.id, false)
                    }
                }
                item { Spacer(Modifier.height(6.dp)); Text("WEEKLY CHALLENGES", style = MaterialTheme.typography.labelLarge, color = PlasmaMagenta) }
                items(save.weeklyChallenges) { ch ->
                    ChallengeRow(ch.title, ch.current, ch.target, "${ch.rewardCoins}c + ${ch.rewardXp} XP", ch.claimed) {
                        onClaimChallenge(ch.id, true)
                    }
                }
            }
            1 -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                items(ProgressionCatalog.LOGIN_STREAK) { rew ->
                    val unlocked = rew.day <= save.loginStreakDay
                    val claimed = rew.day in save.claimedLoginDays
                    Surface(
                        color = SlateArmor,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, if (unlocked && !claimed) BioEmerald else SlateBorder),
                        modifier = Modifier.width(136.dp).height(164.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("DAY ${rew.day}", style = MaterialTheme.typography.headlineMedium, color = LegendaryAmber)
                            Text(rew.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Button(
                                onClick = { onClaimLoginDay(rew.day) },
                                enabled = unlocked && !claimed,
                                colors = ButtonDefaults.buttonColors(containerColor = BioEmerald, contentColor = VoidObsidian),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (claimed) "CLAIMED" else if (unlocked) "CLAIM" else "LOCKED", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(ProgressionCatalog.ACHIEVEMENTS) { ach ->
                    val prog = ach.progressProvider(save.stats, save.level, save.rp).coerceAtMost(ach.targetValue)
                    val claimed = ach.id in save.claimedAchievements
                    ChallengeRow(
                        title = "${ach.title} — ${ach.description}",
                        current = prog,
                        target = ach.targetValue,
                        reward = "${ach.rewardCoins}c + ${ach.rewardGems}g",
                        claimed = claimed
                    ) { onClaimAchievement(ach) }
                }
            }
        }
    }
}

@Composable
private fun ChallengeRow(title: String, current: Int, target: Int, reward: String, claimed: Boolean, onClaim: () -> Unit) {
    val complete = current >= target
    Surface(
        color = SlateArmor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (complete && !claimed) BioEmerald else SlateBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                LinearProgressIndicator(
                    progress = { (current.toFloat() / target.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (complete) BioEmerald else NeonCyan,
                    trackColor = SlateBorder
                )
                Text("$current / $target  ·  Reward: $reward", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Button(
                onClick = onClaim,
                enabled = complete && !claimed,
                colors = ButtonDefaults.buttonColors(containerColor = BioEmerald, contentColor = VoidObsidian),
                modifier = Modifier.height(38.dp)
            ) {
                Text(if (claimed) "DONE" else if (complete) "CLAIM" else "IN PROGRESS", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ============================================================================
// 4. CAREER STATS & WEAPON MASTERY
// ============================================================================

@Composable
fun StatsAndMasteryScreen(
    save: PlayerSaveData,
    onBack: () -> Unit,
    onClaimMastery: (WeaponClass, Int) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val s = save.stats
    val (tier, div) = RankTier.fromRp(save.rp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
    ) {
        MetaScreenHeader("CAREER & MASTERY", "${tier.title} ${RankTier.divisionRoman(div)} (${save.rp} RP)", save, onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            ToggleChip("CAREER STATS", tab == 0) { tab = 0 }
            ToggleChip("WEAPON MASTERY (10)", tab == 1) { tab = 1 }
        }

        if (tab == 0) {
            val cards = listOf(
                "VICTORIES" to "${s.wins}",
                "ELIMINATIONS" to "${s.totalKills}",
                "K / D RATIO" to "%.2f".format(s.kdRatio),
                "BEST PLACEMENT" to if (s.matchesPlayed == 0) "-" else "#${s.bestPlacement}",
                "LONGEST SURVIVAL" to "${s.longestSurvivalSec / 60}m ${s.longestSurvivalSec % 60}s",
                "ACCURACY" to "%.1f%%".format(s.accuracyPct),
                "HEADSHOTS" to "${s.totalHeadshots}",
                "BEST DAMAGE" to "${s.bestDamageInMatch}",
                "BEST KILLS" to "${s.bestKillsInMatch}",
                "MATCHES PLAYED" to "${s.matchesPlayed}"
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(cards) { (k, v) ->
                    Surface(color = SlateArmor, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, SlateBorder)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(k, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text(v, style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                        }
                    }
                }
            }
        } else {
            val masteries = remember(save.weaponMasteryXp) { ProgressionManager.weaponMasteryList(save) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(masteries) { (wc, level, prog) ->
                    val nextMilestone = listOf(2, 3, 5).firstOrNull { "${wc.id}_$it" !in save.claimedMasteryRewards } ?: 5
                    val canClaim = level >= nextMilestone && "${wc.id}_$nextMilestone" !in save.claimedMasteryRewards
                    Surface(color = SlateArmor, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (canClaim) LegendaryAmber else SlateBorder)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text("${wc.displayName}  ·  Mastery Lv.$level", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = LegendaryAmber,
                                    trackColor = SlateBorder
                                )
                                Text("Next Unlock Lv.$nextMilestone: ${ProgressionCatalog.masteryRewardLabel(wc, nextMilestone)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Button(
                                onClick = { onClaimMastery(wc, nextMilestone) },
                                enabled = canClaim,
                                colors = ButtonDefaults.buttonColors(containerColor = LegendaryAmber, contentColor = VoidObsidian),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(if (canClaim) "CLAIM" else "LV.$nextMilestone", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. 4-HOUR FREE LUCKY SPIN MODAL WITH ANIMATED WHEEL
// ============================================================================

@Composable
fun LuckySpinModal(
    save: PlayerSaveData,
    lastResult: LuckySpinSlot?,
    onSpin: () -> LuckySpinSlot?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val angle = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<LuckySpinSlot?>(lastResult) }
    val ready = ProgressionManager.canFreeSpin(save)
    val remMs = ProgressionManager.millisUntilFreeSpin(save)
    val remStr = "%02d:%02d:%02d".format(remMs / 3_600_000L, (remMs % 3_600_000L) / 60_000L, (remMs % 60_000L) / 1000L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian.copy(alpha = 0.86f))
            .clickable(enabled = !spinning, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SlateArmor,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, PlasmaMagenta),
            modifier = Modifier.width(520.dp).clickable(enabled = false) {}
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                // Wheel Canvas
                Box(modifier = Modifier.size(196.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val slots = ProgressionCatalog.LUCKY_SPIN_SLOTS
                        val sweep = 360f / slots.size
                        rotate(angle.value) {
                            slots.forEachIndexed { idx, slot ->
                                drawArc(
                                    color = slot.rarity.color.copy(alpha = 0.85f),
                                    startAngle = idx * sweep,
                                    sweepAngle = sweep - 2f,
                                    useCenter = true,
                                    topLeft = Offset(8f, 8f),
                                    size = Size(size.width - 16f, size.height - 16f)
                                )
                            }
                        }
                        drawCircle(VoidObsidian, radius = 26f)
                        drawCircle(NeonCyan, radius = 14f)
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LUCKY DROP SPIN", style = MaterialTheme.typography.headlineMedium, color = PlasmaMagenta)
                    Text("Free spin every 4 hours for Coins, Gems & Legendary Skins!", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    if (revealed != null) {
                        Surface(color = revealed!!.rarity.color.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, revealed!!.rarity.color)) {
                            Text(
                                "WON: ${revealed!!.label.uppercase()}!",
                                style = MaterialTheme.typography.titleMedium,
                                color = revealed!!.rarity.color,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!spinning && ready) {
                                    spinning = true
                                    revealed = null
                                    val won = onSpin()
                                    scope.launch {
                                        val idx = ProgressionCatalog.LUCKY_SPIN_SLOTS.indexOf(won).coerceAtLeast(0)
                                        val target = angle.value + 1440f + (360f - idx * 45f)
                                        angle.animateTo(target, tween(2200, easing = FastOutSlowInEasing))
                                        revealed = won
                                        spinning = false
                                    }
                                }
                            },
                            enabled = ready && !spinning,
                            colors = ButtonDefaults.buttonColors(containerColor = PlasmaMagenta, contentColor = TextPrimary),
                            modifier = Modifier.testTag("spin_now_button")
                        ) {
                            Text(if (spinning) "SPINNING..." else if (ready) "FREE SPIN" else "NEXT: $remStr", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateElevated, contentColor = TextPrimary)
                        ) {
                            Text("CLOSE", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) NeonCyan.copy(alpha = 0.22f) else SlateArmor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) NeonCyan else SlateBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) NeonCyan else TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
