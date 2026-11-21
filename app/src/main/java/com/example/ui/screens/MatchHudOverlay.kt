package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.config.WorldConfig
import com.example.game.engine.MatchEngine
import com.example.game.engine.MatchHudState
import com.example.game.engine.MatchPhase
import com.example.game.engine.ScopeMode
import com.example.game.entities.DropPhase
import com.example.game.save.GameSettings
import com.example.game.save.HudButtonLayout
import com.example.ui.theme.BioEmerald
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PlasmaMagenta
import com.example.ui.theme.SlateArmor
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.StormViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MatchHudOverlay(
    engine: MatchEngine,
    settings: GameSettings,
    showFullMap: Boolean,
    onToggleMap: () -> Unit,
    onPause: () -> Unit
) {
    var hud by remember(engine) { mutableStateOf(engine.hudSnapshot) }

    // Poll throttled immutable snapshot at ~30 Hz without coupling to GL frame rendering
    LaunchedEffect(engine) {
        while (true) {
            hud = engine.hudSnapshot
            delay(32L)
        }
    }

    // 1. Matchmaking "Finding players 14/30" Animated Overlay
    if (hud.phase == MatchPhase.MATCHMAKING) {
        Box(
            modifier = Modifier.fillMaxSize().background(VoidObsidian.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = SlateArmor,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, NeonCyan),
                modifier = Modifier.width(360.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = NeonCyan, strokeWidth = 3.dp)
                    Text("FINDING PLAYERS ${hud.matchmakingFound}/30", style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                    Text("Island: ${hud.timeOfDay.label} · 8 POIs · 7 Storm Stages", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    LinearProgressIndicator(
                        progress = { hud.matchmakingFound / 30f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = NeonCyan,
                        trackColor = SlateBorder
                    )
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val hPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        // --- FULLSCREEN ATMOSPHERIC TINTS (Low-HP vignette, Storm purple tint, Flashbang whiteout) ---
        if (hud.hp < 32f && hud.phase == MatchPhase.ACTIVE) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color.Transparent, CrimsonDanger.copy(alpha = 0.42f)))
                )
            )
        }
        if (hud.inStorm) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color.Transparent, StormViolet.copy(alpha = 0.45f)))
                )
            )
        }
        if (hud.flashBlindAlpha > 0.02f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = hud.flashBlindAlpha * 0.92f)))
        }

        // --- TOUCH LOOK DRAG SURFACE (Right 58% of screen) ---
        if (hud.phase == MatchPhase.ACTIVE || hud.phase == MatchPhase.IN_PLANE) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.58f)
                    .fillMaxHeight()
                    .pointerInput(engine) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var prev = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dx = change.position.x - prev.x
                                val dy = change.position.y - prev.y
                                prev = change.position
                                engine.input.addLookDelta(dx * 0.0055f, -dy * 0.0045f)
                            }
                        }
                    }
            )
        }

        // --- SNIPER / RED-DOT FIRST-PERSON SCOPE & CENTER CROSSHAIR ---
        if (hud.phase == MatchPhase.ACTIVE && hud.dropPhase == DropPhase.LANDED) {
            ScopeAndCrosshairCanvas(hud)
        }

        // --- FLOATING POOLED DAMAGE NUMBERS ---
        hud.floatingDamageNumbers.forEach { d ->
            val col = when {
                d.isHeadshot -> LegendaryAmber
                d.isShield -> NeonCyan
                else -> TextPrimary
            }
            Text(
                text = if (d.isHeadshot) "${d.amount}!" else "${d.amount}",
                style = MaterialTheme.typography.labelLarge,
                color = col.copy(alpha = d.alpha),
                fontSize = if (d.isHeadshot) 16.sp else 13.sp,
                modifier = Modifier.offset {
                    IntOffset((d.normX * wPx - 14f).roundToInt(), (d.normY * hPx - 14f).roundToInt())
                }
            )
        }

        // --- SAFE AREA HUD CHROME ---
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {

            // TOP-LEFT: Pause, FPS, Flashlight (at night), Bush stealth badge
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onPause,
                    modifier = Modifier.size(48.dp).background(SlateArmor.copy(alpha = 0.72f), CircleShape).testTag("hud_pause_button")
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = TextPrimary)
                }
                if (settings.showFps) {
                    HudChip("${hud.fps} FPS", NeonCyan)
                }
                if (hud.timeOfDay.isNight) {
                    IconButton(
                        onClick = { engine.input.flashlightPressed.set(true) },
                        modifier = Modifier.size(44.dp).background(
                            if (hud.flashlightOn) LegendaryAmber.copy(alpha = 0.35f) else SlateArmor.copy(alpha = 0.72f),
                            CircleShape
                        )
                    ) {
                        Icon(Icons.Default.FlashlightOn, contentDescription = "Flashlight", tint = if (hud.flashlightOn) LegendaryAmber else TextSecondary)
                    }
                }
                if (hud.isHiddenInBush) {
                    HudChip("CONCEALED", BioEmerald)
                }
                if (hud.speedBoostActive) {
                    HudChip("VOLT BOOST", LegendaryAmber)
                }
            }

            // TOP-CENTER: Compass + Storm Timer + Alive/Kills Counters
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompassStrip(hud)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    HudChip("ALIVE ${hud.playersAlive}", NeonCyan)
                    HudChip("KILLS ${hud.kills}", PlasmaMagenta)
                    HudChip(
                        text = "ZONE ${hud.stormStage} ${if (hud.stormShrinking) "SHRINK" else "WAIT"} ${hud.stormTimerSec}s",
                        accent = if (hud.inStorm || hud.stormShrinking) StormViolet else TextPrimary
                    )
                }
                hud.killStreakText?.let { streak ->
                    Surface(color = PlasmaMagenta.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp)) {
                        Text(streak, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                    }
                }
                hud.airdropBannerText?.let { dropTxt ->
                    Surface(color = LegendaryAmber.copy(alpha = 0.88f), shape = RoundedCornerShape(8.dp)) {
                        Text(dropTxt, style = MaterialTheme.typography.labelLarge, color = VoidObsidian, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
                    }
                }
            }

            // TOP-RIGHT: Tappable Circular Minimap + Kill Feed
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniMapWidget(hud = hud, onClick = onToggleMap)
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    hud.killFeed.take(4).forEach { kf ->
                        Surface(
                            color = if (kf.involvesHuman) PlasmaMagenta.copy(alpha = 0.35f) else VoidObsidian.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${kf.killer} [${kf.weaponName}${if (kf.isHeadshot) " HS" else ""}] ${kf.victim}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (kf.involvesHuman) LegendaryAmber else TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // PLANE JUMP CTA or ALTITUDE READOUT
            if (hud.dropPhase == DropPhase.IN_PLANE && hud.phase == MatchPhase.IN_PLANE) {
                Button(
                    onClick = { engine.input.planeJumpPressed.set(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidObsidian),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .height(54.dp)
                        .testTag("plane_jump_button")
                ) {
                    Text("JUMP FROM PLANE", style = MaterialTheme.typography.headlineMedium)
                }
            } else if (hud.dropPhase == DropPhase.FREEFALL || hud.dropPhase == DropPhase.PARACHUTE) {
                HudChip(
                    "${if (hud.dropPhase == DropPhase.PARACHUTE) "PARACHUTE" else "FREEFALL"} · ALT ${hud.altitudeMeters}m",
                    NeonCyan,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp)
                )
            }

            // HEALING CHANNEL PROGRESS RING
            hud.healingLabel?.let { hLabel ->
                Surface(
                    color = SlateArmor.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BioEmerald),
                    modifier = Modifier.align(Alignment.Center).offset(y = 58.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(progress = { hud.healingProgress }, color = BioEmerald, modifier = Modifier.size(20.dp), strokeWidth = 3.dp)
                        Text("USING $hLabel...", style = MaterialTheme.typography.labelMedium, color = BioEmerald)
                    }
                }
            }

            // SPECTATE MODE BAR (with Cycle Bot + Skip to Results)
            if (hud.phase == MatchPhase.SPECTATING) {
                Surface(
                    color = SlateArmor.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, PlasmaMagenta),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Spectating", tint = PlasmaMagenta)
                        Column {
                            Text("SPECTATING: ${hud.spectateTargetName ?: "BOT"}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("HP ${hud.spectateTargetHp.toInt()} · ${hud.playersAlive} ALIVE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Button(
                            onClick = { engine.input.spectateNextPressed.set(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder, contentColor = TextPrimary)
                        ) { Text("NEXT", style = MaterialTheme.typography.labelMedium) }
                        Button(
                            onClick = { engine.skipSpectateToResults() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidObsidian),
                            modifier = Modifier.testTag("spectate_skip_button")
                        ) { Text("SKIP TO RESULTS", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }

            // BOTTOM-CENTER: HP / Shield / Stamina / Armor + 5 Weapon Slots
            if (hud.phase == MatchPhase.ACTIVE) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Interact Loot / Airdrop banner
                    hud.interactPrompt?.let { prompt ->
                        Surface(
                            color = LegendaryAmber.copy(alpha = 0.90f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { engine.input.pickupPressed.set(true) }
                        ) {
                            Text(prompt, style = MaterialTheme.typography.labelLarge, color = VoidObsidian, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                    }

                    // Vehicle HUD strip when driving
                    if (hud.inVehicle) {
                        HudChip("JEEP ${hud.vehicleSpeedKmh} KM/H · HP ${hud.vehicleHp.toInt()}/${hud.vehicleMaxHp.toInt()}", LegendaryAmber)
                    }

                    // Shield, HP & Stamina Bars
                    Surface(
                        color = VoidObsidian.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.width(260.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SHD ${hud.shield.toInt()}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                Text("HP ${hud.hp.toInt()}", style = MaterialTheme.typography.labelSmall, color = if (hud.hp < 35f) CrimsonDanger else BioEmerald)
                                Text("V${hud.armorLevel} H${hud.helmetLevel} B${hud.backpackLevel}", style = MaterialTheme.typography.labelSmall, color = LegendaryAmber)
                            }
                            LinearProgressIndicator(
                                progress = { hud.shield / WorldConfig.MAX_SHIELD },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = NeonCyan, trackColor = SlateBorder
                            )
                            LinearProgressIndicator(
                                progress = { hud.hp / WorldConfig.MAX_HP },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (hud.hp < 35f) CrimsonDanger else BioEmerald, trackColor = SlateBorder
                            )
                            LinearProgressIndicator(
                                progress = { hud.stamina / WorldConfig.MAX_STAMINA },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                                color = LegendaryAmber, trackColor = SlateBorder
                            )
                        }
                    }

                    // 5 Weapon Slots
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        hud.slots.forEach { slot ->
                            val selected = slot.slotIndex == hud.activeSlot
                            val borderCol = if (selected) NeonCyan else (slot.rarity?.color ?: SlateBorder)
                            Surface(
                                color = if (selected) SlateArmor.copy(alpha = 0.92f) else VoidObsidian.copy(alpha = 0.68f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(if (selected) 2.dp else 1.dp, borderCol),
                                modifier = Modifier
                                    .width(66.dp)
                                    .height(48.dp)
                                    .clickable { engine.input.selectSlot.set(slot.slotIndex) }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = slot.weaponClass?.displayName?.split(" ")?.lastOrNull() ?: "${slot.slotIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = slot.rarity?.color ?: TextSecondary,
                                        maxLines = 1
                                    )
                                    if (slot.weaponClass != null) {
                                        Text("${slot.magAmmo}/${slot.reserveAmmo}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // LEFT VIRTUAL MULTI-TOUCH JOYSTICK
                VirtualJoystick(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 22.dp),
                    onMove = { mx, my ->
                        engine.input.moveX = mx
                        engine.input.moveY = my
                    }
                )
            }
        }

        // --- CUSTOM-POSITIONED MULTI-TOUCH ACTION BUTTONS (from Edit HUD layout) ---
        if (hud.phase == MatchPhase.ACTIVE && hud.dropPhase == DropPhase.LANDED) {
            val layout = settings.hudLayout.ifEmpty { GameSettings.defaultHudLayout() }

            @Composable
            fun placedBtn(
                key: String, label: String, accent: Color, active: Boolean = false,
                holdMode: Boolean = false, onHold: ((Boolean) -> Unit)? = null, onClick: (() -> Unit)? = null
            ) {
                val lay: HudButtonLayout = layout[key] ?: return
                val szDp = (54f * lay.scale).dp.coerceAtLeast(48.dp)
                val szPx = with(density) { szDp.toPx() }
                Surface(
                    color = if (active) accent.copy(alpha = 0.55f) else VoidObsidian.copy(alpha = 0.65f),
                    shape = CircleShape,
                    border = BorderStroke(2.dp, accent),
                    modifier = Modifier
                        .offset { IntOffset((lay.xFrac * wPx - szPx * 0.5f).roundToInt(), (lay.yFrac * hPx - szPx * 0.5f).roundToInt()) }
                        .size(szDp)
                        .testTag("hud_btn_$key")
                        .then(
                            if (holdMode && onHold != null) {
                                Modifier.pointerInput(key) {
                                    awaitEachGesture {
                                        val d = awaitFirstDown(requireUnconsumed = false)
                                        onHold(true)
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val ch = ev.changes.firstOrNull { it.id == d.id } ?: break
                                            if (!ch.pressed) break
                                        }
                                        onHold(false)
                                    }
                                }
                            } else {
                                Modifier.clickable { onClick?.invoke() }
                            }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            placedBtn("fire", "FIRE", PlasmaMagenta, holdMode = true, onHold = { engine.input.fireHeld = it })
            placedBtn("aim", "ADS", NeonCyan, active = hud.scopeMode != ScopeMode.NONE, holdMode = true, onHold = { engine.input.aimHeld = it })
            placedBtn("jump", "JUMP", NeonCyan) { engine.input.jumpPressed.set(true) }
            placedBtn("crouch", if (hud.isCrouching) "UP" else "CROUCH", TextSecondary, active = hud.isCrouching) { engine.input.crouchPressed.set(true) }
            placedBtn("reload", if (hud.reloading) "..." else "RLD", LegendaryAmber) { engine.input.reloadPressed.set(true) }
            if (hud.interactPrompt != null) {
                placedBtn("pickup", "LOOT", LegendaryAmber, active = true) { engine.input.pickupPressed.set(true) }
            }
            placedBtn("grenade", "${hud.selectedGrenade.name.take(3)} ${hud.grenadeCount}", PlasmaMagenta) { engine.input.grenadePressed.set(true) }
            placedBtn("heal", "MED ${hud.availableHealCount}", BioEmerald) { engine.input.healPressed.set(true) }
            placedBtn("sprint", "RUN", NeonCyan, active = engine.input.sprintLocked) {
                engine.input.sprintLocked = !engine.input.sprintLocked
            }
            if (hud.canEnterVehicle || hud.inVehicle) {
                placedBtn("vehicle", if (hud.inVehicle) "EXIT" else "DRIVE", LegendaryAmber, active = hud.inVehicle) {
                    engine.input.vehicleOrMeleePressed.set(true)
                }
            }
        }

        // --- FULL-SCREEN PUBG A1-H8 GRID TACTICAL MAP WITH TAP-TO-PIN WAYPOINT ---
        if (showFullMap) {
            FullTacticalMapModal(
                hud = hud,
                onSetWaypoint = { wx, wz -> engine.setWaypoint(wx, wz) },
                onClearWaypoint = { engine.clearWaypoint() },
                onClose = onToggleMap
            )
        }
    }
}

@Composable
private fun VirtualJoystick(modifier: Modifier = Modifier, onMove: (Float, Float) -> Unit) {
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    val maxR = 52f

    Box(
        modifier = modifier
            .size(124.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width * 0.5f, size.height * 0.5f)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            knobX = 0f; knobY = 0f
                            onMove(0f, 0f)
                            break
                        }
                        val dx = ch.position.x - center.x
                        val dy = ch.position.y - center.y
                        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                        val clamped = len.coerceAtMost(maxR)
                        knobX = (dx / len) * clamped
                        knobY = (dy / len) * clamped
                        onMove(knobX / maxR, -knobY / maxR)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width * 0.5f, size.height * 0.5f)
            drawCircle(VoidObsidian.copy(alpha = 0.48f), radius = size.minDimension * 0.46f, center = c)
            drawCircle(NeonCyan.copy(alpha = 0.55f), radius = size.minDimension * 0.46f, center = c, style = Stroke(2.5f))
            drawCircle(NeonCyan.copy(alpha = 0.85f), radius = 22f, center = Offset(c.x + knobX, c.y + knobY))
        }
    }
}

@Composable
private fun ScopeAndCrosshairCanvas(hud: MatchHudState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f

        // Directional Damage Arc Indicators
        hud.damageIndicators.forEach { ind ->
            val deg = Math.toDegrees(ind.angleRad.toDouble()).toFloat() - 90f
            drawArc(
                color = CrimsonDanger.copy(alpha = ind.alpha * 0.85f),
                startAngle = deg - 18f,
                sweepAngle = 36f,
                useCenter = false,
                topLeft = Offset(cx - 88f, cy - 88f),
                size = androidx.compose.ui.geometry.Size(176f, 176f),
                style = Stroke(width = 8f)
            )
        }

        when (hud.scopeMode) {
            ScopeMode.SNIPER -> {
                val r = size.minDimension * 0.42f
                drawCircle(Color.Black.copy(alpha = 0.85f), radius = size.maxDimension, style = Stroke(width = size.maxDimension * 2f - r * 2f))
                drawCircle(NeonCyan.copy(alpha = 0.8f), radius = r, center = Offset(cx, cy), style = Stroke(3f))
                drawLine(NeonCyan.copy(alpha = 0.75f), Offset(cx - r, cy), Offset(cx + r, cy), 1.8f)
                drawLine(NeonCyan.copy(alpha = 0.75f), Offset(cx, cy - r), Offset(cx, cy + r), 1.8f)
                drawCircle(PlasmaMagenta, radius = 3.5f, center = Offset(cx, cy))
            }
            ScopeMode.RED_DOT -> {
                drawCircle(NeonCyan.copy(alpha = 0.45f), radius = 34f, center = Offset(cx, cy), style = Stroke(2.5f))
                drawCircle(PlasmaMagenta, radius = 4.5f, center = Offset(cx, cy))
            }
            ScopeMode.NONE -> {
                val gap = 7f
                val arm = 12f
                drawLine(NeonCyan, Offset(cx - gap - arm, cy), Offset(cx - gap, cy), 2.4f)
                drawLine(NeonCyan, Offset(cx + gap, cy), Offset(cx + gap + arm, cy), 2.4f)
                drawLine(NeonCyan, Offset(cx, cy - gap - arm), Offset(cx, cy - gap), 2.4f)
                drawLine(NeonCyan, Offset(cx, cy + gap), Offset(cx, cy + gap + arm), 2.4f)
            }
        }

        // Hit Marker X (White body / Gold headshot)
        if (hud.hitMarkerAlpha > 0.05f) {
            val hmCol = (if (hud.hitMarkerHeadshot) LegendaryAmber else Color.White).copy(alpha = hud.hitMarkerAlpha)
            val d1 = 6f; val d2 = 16f
            drawLine(hmCol, Offset(cx - d2, cy - d2), Offset(cx - d1, cy - d1), 3f)
            drawLine(hmCol, Offset(cx + d1, cy - d1), Offset(cx + d2, cy - d2), 3f)
            drawLine(hmCol, Offset(cx - d2, cy + d2), Offset(cx - d1, cy + d1), 3f)
            drawLine(hmCol, Offset(cx + d1, cy + d1), Offset(cx + d2, cy + d2), 3f)
        }
    }
}

@Composable
private fun CompassStrip(hud: MatchHudState) {
    val deg = ((Math.toDegrees(hud.playerYawRad.toDouble()).toInt() % 360) + 360) % 360
    val cardinal = when {
        deg in 338..360 || deg in 0..22 -> "N"
        deg in 23..67 -> "NE"
        deg in 68..112 -> "E"
        deg in 113..157 -> "SE"
        deg in 158..202 -> "S"
        deg in 203..247 -> "SW"
        deg in 248..292 -> "W"
        else -> "NW"
    }
    val pinInfo = if (hud.waypointX != null && hud.waypointZ != null) {
        val dx = hud.waypointX - hud.playerX
        val dz = hud.waypointZ - hud.playerZ
        val distM = sqrt(dx * dx + dz * dz).toInt()
        val pinDeg = ((Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toInt() % 360) + 360) % 360
        "  |  PIN ${pinDeg}° (${distM}m)"
    } else ""

    Surface(color = VoidObsidian.copy(alpha = 0.78f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, SlateBorder)) {
        Text(
            text = "◄ ${hud.gridSquare} · $cardinal %03d°$pinInfo ►".format(deg),
            style = MaterialTheme.typography.labelMedium,
            color = if (pinInfo.isNotEmpty()) LegendaryAmber else TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun MiniMapWidget(hud: MatchHudState, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // PUBG / GTA Style Square GPS Road Radar
        Surface(
            color = Color(0xFF1C261F).copy(alpha = 0.88f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(2.dp, SlateBorder),
            modifier = Modifier.size(106.dp).clickable(onClick = onClick).testTag("hud_minimap_button")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val scale = size.width / 460f // 460m tactical city radar span
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)

                // Building block footprints (GTA city radar blocks!)
                hud.buildingFootprints.forEach { b ->
                    val bx0 = cx + (b.minX - hud.playerX) * scale
                    val bz0 = cy + (b.minZ - hud.playerZ) * scale
                    val bw = (b.maxX - b.minX) * scale
                    val bh = (b.maxZ - b.minZ) * scale
                    if (bx0 + bw >= -10f && bx0 <= size.width + 10f && bz0 + bh >= -10f && bz0 <= size.height + 10f) {
                        drawRect(
                            color = if (b.isDowntown) Color(0xFF4A515A) else Color(0xFF5A5248),
                            topLeft = Offset(bx0, bz0),
                            size = androidx.compose.ui.geometry.Size(bw.coerceAtLeast(2f), bh.coerceAtLeast(2f))
                        )
                    }
                }

                // Paved roads & highways overlay (GTA GPS street style)
                hud.roadSegments.forEach { seg ->
                    val x1 = cx + (seg.x1 - hud.playerX) * scale
                    val y1 = cy + (seg.z1 - hud.playerZ) * scale
                    val x2 = cx + (seg.x2 - hud.playerX) * scale
                    val y2 = cy + (seg.z2 - hud.playerZ) * scale
                    drawLine(
                        color = if (seg.isDirt) Color(0xFF7B674A) else Color(0xFF9EA6B0),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = (seg.width * scale).coerceIn(2.2f, 6.5f)
                    )
                }

                // Safe-zone dashed guidance line (PUBG white guide line)
                val nx = cx + (hud.nextStormCenterX - hud.playerX) * scale
                val ny = cy + (hud.nextStormCenterZ - hud.playerZ) * scale
                drawLine(Color.White.copy(alpha = 0.65f), Offset(cx, cy), Offset(nx, ny), 1.5f, pathEffect = dash)

                // Current storm ring & next white safe-zone ring
                val sx = cx + (hud.stormCenterX - hud.playerX) * scale
                val sy = cy + (hud.stormCenterZ - hud.playerZ) * scale
                drawCircle(Color(0xFF3B82F6), radius = hud.stormRadius * scale, center = Offset(sx, sy), style = Stroke(2.5f))
                drawCircle(Color.White.copy(alpha = 0.85f), radius = hud.nextStormRadius * scale, center = Offset(nx, ny), style = Stroke(1.6f))

                // Waypoint Pin + GPS route line (clamped to radar edge if distant)
                if (hud.waypointX != null && hud.waypointZ != null) {
                    val wx = cx + (hud.waypointX - hud.playerX) * scale
                    val wy = cy + (hud.waypointZ - hud.playerZ) * scale
                    val clampedX = wx.coerceIn(7f, size.width - 7f)
                    val clampedY = wy.coerceIn(7f, size.height - 7f)
                    drawLine(LegendaryAmber.copy(alpha = 0.85f), Offset(cx, cy), Offset(clampedX, clampedY), 2f, pathEffect = dash)
                    drawCircle(LegendaryAmber, radius = 5f, center = Offset(clampedX, clampedY))
                }

                // Dynamic Blips (Airdrops, Vehicles, Gunshots)
                hud.minimapDots.forEach { dot ->
                    val dx = cx + (dot.x - hud.playerX) * scale
                    val dy = cy + (dot.z - hud.playerZ) * scale
                    if (dx in 0f..size.width && dy in 0f..size.height) {
                        val col = when {
                            dot.isAirdrop -> LegendaryAmber
                            dot.isEnemyShot -> CrimsonDanger
                            else -> Color(0xFFD1D5DB)
                        }
                        drawCircle(col, radius = if (dot.isAirdrop) 4.5f else 2.8f, center = Offset(dx, dy))
                    }
                }

                // Player Tactical Direction Arrow at Center
                val fx = sin(hud.playerYawRad)
                val fz = cos(hud.playerYawRad)
                val path = Path().apply {
                    moveTo(cx + fx * 8f, cy + fz * 8f)
                    lineTo(cx - fx * 5f - fz * 5f, cy - fz * 5f + fx * 5f)
                    lineTo(cx - fx * 5f + fz * 5f, cy - fz * 5f - fx * 5f)
                    close()
                }
                drawPath(path, LegendaryAmber)
            }
        }
        Text("${hud.gridSquare} · MAP", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun FullTacticalMapModal(
    hud: MatchHudState,
    onSetWaypoint: (Float, Float) -> Unit,
    onClearWaypoint: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(VoidObsidian.copy(alpha = 0.90f)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF141B22),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(2.dp, SlateBorder),
            modifier = Modifier.width(520.dp).height(352.dp).clickable(enabled = false) {}
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Square PUBG A1-H8 Topographic Grid Map Canvas
                Box(
                    modifier = Modifier
                        .size(326.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF132A3E))
                        .border(1.5.dp, SlateBorder, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val wx = ((offset.x / size.width) * WorldConfig.WORLD_SIZE) - WorldConfig.HALF_WORLD
                                val wz = ((offset.y / size.height) * WorldConfig.WORLD_SIZE) - WorldConfig.HALF_WORLD
                                onSetWaypoint(wx, wz)
                            }
                        }
                        .testTag("pubg_grid_map_canvas")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val toX = { wx: Float -> ((wx + WorldConfig.HALF_WORLD) / WorldConfig.WORLD_SIZE) * size.width }
                        val toY = { wz: Float -> ((wz + WorldConfig.HALF_WORLD) / WorldConfig.WORLD_SIZE) * size.height }
                        val rScale = size.width / WorldConfig.WORLD_SIZE
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

                        // Island Topographic Base (PUBG Erangel earthy land mass + biome tints)
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        drawCircle(Color(0xFF4C5E3D), radius = size.minDimension * 0.43f, center = Offset(cx, cy))
                        // Subtle biome quadrant washes (Snow NE, Desert SE, Swamp SW)
                        drawRect(Color(0xFF8C979E).copy(alpha = 0.36f), Offset(cx, size.height * 0.10f), androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.40f))
                        drawRect(Color(0xFF8C724A).copy(alpha = 0.38f), Offset(cx, cy), androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.40f))
                        drawRect(Color(0xFF34412D).copy(alpha = 0.35f), Offset(size.width * 0.10f, cy), androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.40f))
                        // Downtown Concrete City Core
                        drawRect(Color(0xFF42474E), Offset(toX(-210f), toY(-210f)), androidx.compose.ui.geometry.Size(420f * rScale, 420f * rScale))

                        // Building Footprints
                        hud.buildingFootprints.forEach { b ->
                            drawRect(
                                Color(0xFF7B828C),
                                Offset(toX(b.minX), toY(b.minZ)),
                                androidx.compose.ui.geometry.Size(((b.maxX - b.minX) * rScale).coerceAtLeast(1.8f), ((b.maxZ - b.minZ) * rScale).coerceAtLeast(1.8f))
                            )
                        }

                        // Full GTA Highway & Downtown Street Network
                        hud.roadSegments.forEach { seg ->
                            drawLine(
                                color = if (seg.isDirt) Color(0xFF9E8762) else Color(0xFFD6DAE0),
                                start = Offset(toX(seg.x1), toY(seg.z1)),
                                end = Offset(toX(seg.x2), toY(seg.z2)),
                                strokeWidth = if (seg.isHighway) 2.8f else 1.8f
                            )
                        }

                        // PUBG A1-H8 8x8 Tactical Grid Lines & Coordinate Labels
                        val cell = size.width / 8f
                        val labelPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(215, 226, 232, 240)
                            textSize = 18f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        val poiPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(240, 255, 245, 215)
                            textSize = 16f
                            isAntiAlias = true
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
                        }
                        for (g in 1 until 8) {
                            drawLine(Color.White.copy(alpha = 0.22f), Offset(g * cell, 0f), Offset(g * cell, size.height), 1f)
                            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, g * cell), Offset(size.width, g * cell), 1f)
                        }
                        for (g in 0 until 8) {
                            val colLetter = ('A'.code + g).toChar().toString()
                            drawContext.canvas.nativeCanvas.drawText(colLetter, g * cell + 5f, 17f, labelPaint)
                            drawContext.canvas.nativeCanvas.drawText("${g + 1}", 4f, g * cell + 34f, labelPaint)
                        }

                        // Transport Plane Flight Path
                        drawLine(
                            Color.White.copy(alpha = 0.72f),
                            Offset(toX(hud.planeStartX), toY(hud.planeStartZ)),
                            Offset(toX(hud.planeEndX), toY(hud.planeEndZ)),
                            2.2f,
                            pathEffect = dash
                        )

                        // Blue Storm Zone & White Next Safe Circle
                        drawCircle(Color(0xFF3B82F6), radius = hud.stormRadius * rScale, center = Offset(toX(hud.stormCenterX), toY(hud.stormCenterZ)), style = Stroke(3.2f))
                        drawCircle(Color.White, radius = hud.nextStormRadius * rScale, center = Offset(toX(hud.nextStormCenterX), toY(hud.nextStormCenterZ)), style = Stroke(2.2f))

                        // Named Towns / POIs
                        hud.minimapPois.forEach { p ->
                            val px = toX(p.x)
                            val py = toY(p.z)
                            drawContext.canvas.nativeCanvas.drawText(p.name.uppercase(), px, py - 4f, poiPaint)
                        }

                        // Airdrops
                        hud.minimapDots.filter { it.isAirdrop }.forEach { d ->
                            drawCircle(CrimsonDanger, radius = 5.5f, center = Offset(toX(d.x), toY(d.z)))
                        }

                        // Player & Waypoint Pin + GPS Dashed Line
                        val plX = toX(hud.playerX)
                        val plY = toY(hud.playerZ)
                        if (hud.waypointX != null && hud.waypointZ != null) {
                            val wpX = toX(hud.waypointX)
                            val wpY = toY(hud.waypointZ)
                            drawLine(LegendaryAmber, Offset(plX, plY), Offset(wpX, wpY), 2.4f, pathEffect = dash)
                            drawCircle(LegendaryAmber, radius = 7f, center = Offset(wpX, wpY))
                            drawCircle(VoidObsidian, radius = 3f, center = Offset(wpX, wpY))
                        }
                        drawCircle(NeonCyan, radius = 6f, center = Offset(plX, plY))
                        drawCircle(Color.White, radius = 6f, center = Offset(plX, plY), style = Stroke(1.8f))
                    }
                }

                // Right Tactical Legend & Waypoint Controls
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TACTICAL MAP", style = MaterialTheme.typography.headlineMedium, color = LegendaryAmber)
                        Text("GRID: ${hud.gridSquare}", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Text("Tap anywhere on the A1–H8 grid to place a GPS Waypoint Pin.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("• White Ring: Next Safe Zone", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        Text("• Blue Ring: Storm Wall", style = MaterialTheme.typography.labelSmall, color = Color(0xFF60A5FA))
                        Text("• Amber Pin: Your Waypoint", style = MaterialTheme.typography.labelSmall, color = LegendaryAmber)
                        Text("• Red Dot: Supply Airdrop", style = MaterialTheme.typography.labelSmall, color = CrimsonDanger)
                        if (hud.waypointX != null && hud.waypointZ != null) {
                            val dist = sqrt((hud.waypointX - hud.playerX) * (hud.waypointX - hud.playerX) + (hud.waypointZ - hud.playerZ) * (hud.waypointZ - hud.playerZ)).toInt()
                            Text("PIN DISTANCE: ${dist}m", style = MaterialTheme.typography.labelLarge, color = LegendaryAmber)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hud.waypointX != null) {
                            Button(
                                onClick = onClearWaypoint,
                                colors = ButtonDefaults.buttonColors(containerColor = SlateBorder, contentColor = LegendaryAmber),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) { Text("CLEAR PIN", style = MaterialTheme.typography.labelLarge) }
                        }
                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidObsidian),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) { Text("CLOSE MAP", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        color = VoidObsidian.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
