package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.config.RankTier
import com.example.game.progression.CosmeticCategory
import com.example.game.progression.ProgressionCatalog
import com.example.game.progression.ProgressionManager
import com.example.game.save.PlayerSaveData
import com.example.ui.theme.BioEmerald
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PlasmaMagenta
import com.example.ui.theme.SlateArmor
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.StormViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import com.example.ui.viewmodel.AppScreen
import kotlin.math.sin

@Composable
fun LobbyScreen(
    save: PlayerSaveData,
    onNavigate: (AppScreen) -> Unit,
    onDeploy: () -> Unit,
    onOpenSpin: () -> Unit,
    onEquipCosmetic: (String) -> Unit
) {
    val (rankTier, rankDiv) = RankTier.fromRp(save.rp)
    val xpNeeded = ProgressionManager.xpRequiredForLevel(save.level)
    val spinReady = ProgressionManager.canFreeSpin(save)
    val unclaimedLogin = save.loginStreakDay !in save.claimedLoginDays
    val unclaimedQuests = save.dailyChallenges.any { it.current >= it.target && !it.claimed } ||
        save.weeklyChallenges.any { it.current >= it.target && !it.claimed } ||
        save.unlockedAchievements.any { it !in save.claimedAchievements }

    val pulse = rememberInfiniteTransition(label = "lobbyPulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val bobPhase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "bob"
    )

    Box(modifier = Modifier.fillMaxSize().background(VoidObsidian)) {
        // Realistic PUBG x GTA Downtown Island Key-Art Backdrop
        Image(
            painter = painterResource(id = R.drawable.img_tactical_city_backdrop_1795254037973),
            contentDescription = "Zone Zero Tactical City Island Backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark-neon tactical gradient vignette ensuring high HUD legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to VoidObsidian.copy(alpha = 0.88f),
                        0.38f to VoidObsidian.copy(alpha = 0.35f),
                        0.72f to VoidObsidian.copy(alpha = 0.48f),
                        1.0f to VoidObsidian.copy(alpha = 0.90f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // --- TOP BAR: Brand Title + Level/Rank + Currencies + Spin/Streak ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            text = "ZONE ZERO",
                            style = MaterialTheme.typography.displayMedium,
                            color = NeonCyan
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "LVL ${save.level}",
                                style = MaterialTheme.typography.labelLarge,
                                color = LegendaryAmber
                            )
                            LinearProgressIndicator(
                                progress = { (save.xp.toFloat() / xpNeeded.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.width(86.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = NeonCyan,
                                trackColor = SlateBorder
                            )
                            Surface(
                                color = rankTier.color.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, rankTier.color)
                            ) {
                                Text(
                                    text = "${rankTier.title.uppercase()} ${RankTier.divisionRoman(rankDiv)} · ${save.rp} RP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = rankTier.color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Lucky Spin Quick CTA
                    Surface(
                        color = if (spinReady) PlasmaMagenta.copy(alpha = 0.25f) else SlateArmor.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (spinReady) PlasmaMagenta else SlateBorder),
                        modifier = Modifier
                            .testTag("lucky_spin_button")
                            .clickable(onClick = onOpenSpin)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Casino, contentDescription = "Lucky Spin", tint = if (spinReady) PlasmaMagenta else TextSecondary, modifier = Modifier.size(18.dp))
                            Text(if (spinReady) "FREE SPIN" else "SPIN", style = MaterialTheme.typography.labelMedium, color = if (spinReady) TextPrimary else TextSecondary)
                        }
                    }

                    // 7-Day Login Streak shortcut
                    Surface(
                        color = if (unclaimedLogin) BioEmerald.copy(alpha = 0.22f) else SlateArmor.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (unclaimedLogin) BioEmerald else SlateBorder),
                        modifier = Modifier
                            .testTag("login_streak_button")
                            .clickable { onNavigate(AppScreen.CHALLENGES) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = "Login Streak", tint = LegendaryAmber, modifier = Modifier.size(18.dp))
                            Text("DAY ${save.loginStreakDay}/7", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                    }

                    CurrencyPill(label = "${save.coins}", accent = LegendaryAmber, tag = "COINS")
                    CurrencyPill(label = "${save.gems}", accent = NeonCyan, tag = "GEMS")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- MAIN BODY: Left Nav Rail + Center Operative Showcase + Right Deploy Panel ---
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Left Vertical Tactical Nav Rail
                Column(
                    modifier = Modifier.width(152.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    LobbyNavItem("SHOP", Icons.Default.ShoppingBag, NeonCyan, false, "nav_shop") { onNavigate(AppScreen.SHOP) }
                    LobbyNavItem("BATTLE PASS", Icons.Default.Star, LegendaryAmber, false, "nav_battle_pass") { onNavigate(AppScreen.BATTLE_PASS) }
                    LobbyNavItem("CHALLENGES", Icons.Default.EmojiEvents, PlasmaMagenta, unclaimedQuests || unclaimedLogin, "nav_challenges") { onNavigate(AppScreen.CHALLENGES) }
                    LobbyNavItem("STATS & MASTERY", Icons.Default.BarChart, BioEmerald, false, "nav_stats") { onNavigate(AppScreen.STATS) }
                    LobbyNavItem("SETTINGS & HUD", Icons.Default.Settings, TextSecondary, false, "nav_settings") { onNavigate(AppScreen.SETTINGS) }
                }

                // Center Character Preview & Quick Skin Switcher
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    val equippedSkin = ProgressionCatalog.COSMETIC_BY_ID[save.equippedCharacterSkin]
                    val equippedChute = ProgressionCatalog.COSMETIC_BY_ID[save.equippedParachute]

                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OperativeAvatarPreview(
                            primary = equippedSkin?.primaryRgb ?: floatArrayOf(0.16f, 0.24f, 0.36f),
                            accent = equippedSkin?.accentRgb ?: floatArrayOf(0f, 0.94f, 1f),
                            chute = equippedChute?.primaryRgb ?: floatArrayOf(0f, 0.94f, 1f),
                            bobOffset = sin(bobPhase) * 6f
                        )
                    }

                    // Quick owned operative skin selector strip
                    val ownedSkins = ProgressionCatalog.COSMETICS.filter {
                        it.category == CosmeticCategory.CHARACTER_SKIN && it.id in save.ownedCosmetics
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(ownedSkins) { skin ->
                            val selected = skin.id == save.equippedCharacterSkin
                            Surface(
                                color = if (selected) NeonCyan.copy(alpha = 0.22f) else SlateArmor.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (selected) NeonCyan else SlateBorder),
                                modifier = Modifier.clickable { onEquipCosmetic(skin.id) }
                            ) {
                                Text(
                                    text = skin.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) NeonCyan else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Right Match Briefing & Primary DEPLOY CTA
                Column(
                    modifier = Modifier.width(236.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Surface(
                        color = SlateArmor.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Text("SOLO ISLAND ROYALE", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                            Text("30 Players · 2000m Island · 7 Storm Stages", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(
                                "BP Tier ${ProgressionManager.battlePassTier(save.battlePassXp)}/30  ·  Wins: ${save.stats.wins}  ·  K/D: ${"%.2f".format(save.stats.kdRatio)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LegendaryAmber
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "WASD / Dual-Thumb · Aim Assist ${if (save.settings.aimAssistEnabled) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Button(
                            onClick = onDeploy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("deploy_play_button")
                                .border(2.dp, NeonCyan.copy(alpha = glowAlpha), RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = VoidObsidian
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Deploy", modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("DEPLOY", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyNavItem(
    label: String,
    icon: ImageVector,
    tint: Color,
    hasBadge: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        color = SlateArmor.copy(alpha = 0.84f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontSize = 13.sp)
            }
            if (hasBadge) {
                Box(modifier = Modifier.size(8.dp).background(PlasmaMagenta, CircleShape))
            }
        }
    }
}

@Composable
fun CurrencyPill(label: String, accent: Color, tag: String) {
    Surface(
        color = SlateArmor.copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(accent, CircleShape))
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Text(tag, style = MaterialTheme.typography.labelSmall, color = accent)
        }
    }
}

@Composable
private fun OperativeAvatarPreview(primary: FloatArray, accent: FloatArray, chute: FloatArray, bobOffset: Float) {
    val jacketCol = Color(primary[0], primary[1], primary[2])
    val vestCol = Color(0.24f, 0.28f, 0.22f)
    val accentCol = Color(accent[0], accent[1], accent[2])
    val pantsCol = Color((primary[0] * 0.55f + 0.14f).coerceIn(0.12f, 0.55f), (primary[1] * 0.55f + 0.14f).coerceIn(0.12f, 0.55f), (primary[2] * 0.5f + 0.12f).coerceIn(0.10f, 0.50f))
    val skinCol = Color(0.82f, 0.66f, 0.54f)
    val steelCol = Color(0.15f, 0.17f, 0.20f)

    Canvas(modifier = Modifier.size(196.dp)) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.53f + bobOffset * 0.45f
        // Ground shadow pad
        drawOval(
            brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), Offset(cx, size.height * 0.90f), 70f),
            topLeft = Offset(cx - 66f, size.height * 0.81f),
            size = Size(132f, 28f)
        )
        // Combat Boots & Articulated Legs + Tactical Kneepads
        drawRoundRect(pantsCol, Offset(cx - 24f, cy + 20f), Size(19f, 54f), CornerRadius(8f, 8f))
        drawRoundRect(pantsCol, Offset(cx + 5f, cy + 20f), Size(19f, 54f), CornerRadius(8f, 8f))
        drawRoundRect(steelCol, Offset(cx - 23f, cy + 40f), Size(17f, 11f), CornerRadius(5f, 5f)) // Kneepad L
        drawRoundRect(steelCol, Offset(cx + 6f, cy + 40f), Size(17f, 11f), CornerRadius(5f, 5f)) // Kneepad R
        drawRoundRect(Color(0xFF1A1816), Offset(cx - 26f, cy + 68f), Size(22f, 14f), CornerRadius(5f, 5f))
        drawRoundRect(Color(0xFF1A1816), Offset(cx + 4f, cy + 68f), Size(22f, 14f), CornerRadius(5f, 5f))

        // Backpack silhouette behind shoulders
        drawRoundRect(Color(0xFF3A3528), Offset(cx - 32f, cy - 32f), Size(64f, 44f), CornerRadius(10f, 10f))

        // Sculpted Torso Jacket & Utility Belt
        drawRoundRect(jacketCol, Offset(cx - 28f, cy - 34f), Size(56f, 54f), CornerRadius(12f, 12f))
        drawRoundRect(Color(0xFF1D1B18), Offset(cx - 26f, cy + 12f), Size(52f, 9f), CornerRadius(3f, 3f))

        // PUBG Ballistic Plate Carrier Vest + Magazine Pouches
        drawRoundRect(vestCol, Offset(cx - 23f, cy - 28f), Size(46f, 40f), CornerRadius(7f, 7f))
        drawRoundRect(steelCol, Offset(cx - 17f, cy - 4f), Size(10f, 13f), CornerRadius(2f, 2f))
        drawRoundRect(steelCol, Offset(cx - 5f, cy - 4f), Size(10f, 13f), CornerRadius(2f, 2f))
        drawRoundRect(steelCol, Offset(cx + 7f, cy - 4f), Size(10f, 13f), CornerRadius(2f, 2f))
        drawRoundRect(accentCol.copy(alpha = 0.85f), Offset(cx - 14f, cy - 20f), Size(28f, 6f), CornerRadius(2f, 2f))

        // Rounded Shoulders & Arms
        drawCircle(jacketCol, radius = 12f, center = Offset(cx - 31f, cy - 22f))
        drawCircle(jacketCol, radius = 12f, center = Offset(cx + 31f, cy - 22f))
        drawRoundRect(jacketCol, Offset(cx - 41f, cy - 22f), Size(14f, 40f), CornerRadius(6f, 6f))
        drawRoundRect(jacketCol, Offset(cx + 27f, cy - 22f), Size(14f, 38f), CornerRadius(6f, 6f))
        // Tactical Gloves
        drawCircle(steelCol, radius = 7f, center = Offset(cx - 34f, cy + 16f))
        drawCircle(steelCol, radius = 7f, center = Offset(cx + 34f, cy + 14f))

        // Neck, Sculpted Head & Lv3 Spetsnaz Tactical Helmet
        drawRoundRect(skinCol.copy(alpha = 0.9f), Offset(cx - 8f, cy - 46f), Size(16f, 14f), CornerRadius(4f, 4f))
        drawCircle(skinCol, radius = 17f, center = Offset(cx, cy - 58f))
        // Helmet Dome & Armored Visor
        drawArc(
            color = Color(0xFF282D32),
            startAngle = 170f,
            sweepAngle = 200f,
            useCenter = true,
            topLeft = Offset(cx - 21f, cy - 82f),
            size = Size(42f, 42f)
        )
        drawRoundRect(Color(0xFF333940), Offset(cx - 17f, cy - 64f), Size(34f, 15f), CornerRadius(4f, 4f))
        drawRoundRect(Color(0xFF0A0D12), Offset(cx - 12f, cy - 60f), Size(24f, 5f), CornerRadius(2f, 2f)) // Eye slit

        // Detailed Tactical Assault Rifle held across chest
        drawRoundRect(steelCol, Offset(cx - 20f, cy - 10f), Size(68f, 11f), CornerRadius(3f, 3f))
        drawRoundRect(Color(0xFF101318), Offset(cx + 46f, cy - 8f), Size(26f, 5f), CornerRadius(2f, 2f)) // Barrel + Suppressor
        drawRoundRect(Color(0xFF2B2F36), Offset(cx + 6f, cy - 17f), Size(20f, 7f), CornerRadius(2f, 2f)) // 4x Scope
        drawRoundRect(Color(0xFF25282E), Offset(cx + 12f, cy + 1f), Size(8f, 16f), CornerRadius(2f, 2f)) // Mag
    }
}
