package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.config.GraphicsQuality
import com.example.game.save.GameSettings
import com.example.game.save.HudButtonLayout
import com.example.game.save.PlayerSaveData
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PlasmaMagenta
import com.example.ui.theme.SlateArmor
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    save: PlayerSaveData,
    onBack: () -> Unit,
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit,
    onOpenHudEditor: () -> Unit,
    onResetSaveData: () -> Unit
) {
    val s = save.settings
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
    ) {
        MetaScreenHeader("SETTINGS & CONTROLS", "Audio, graphics quality, aim assist, gyro & HUD layout", save, onBack)

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Left Column: Audio & Graphics
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsCard("AUDIO & GRAPHICS") {
                    SettingSlider("SFX Volume", s.soundVolume, "${(s.soundVolume * 100).toInt()}%") { v ->
                        onUpdateSettings { it.copy(soundVolume = v) }
                    }
                    SettingSlider("Music Volume", s.musicVolume, "${(s.musicVolume * 100).toInt()}%") { v ->
                        onUpdateSettings { it.copy(musicVolume = v) }
                    }
                    Text("Graphics Quality", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GraphicsQuality.entries.forEach { q ->
                            ToggleChip(q.label.uppercase(), s.qualityEnum() == q) {
                                onUpdateSettings { it.copy(graphicsQuality = q.name) }
                            }
                        }
                    }
                    SettingSwitch("Show FPS Counter", s.showFps) { on ->
                        onUpdateSettings { it.copy(showFps = on) }
                    }
                }
            }

            // Right Column: Controls, Aim, Gyro, Edit HUD & Reset
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsCard("CONTROLS & AIMING") {
                    SettingSlider("Look Sensitivity", (s.lookSensitivity - 0.3f) / 1.7f, "%.2fx".format(s.lookSensitivity)) { norm ->
                        onUpdateSettings { it.copy(lookSensitivity = 0.3f + norm * 1.7f) }
                    }
                    SettingSwitch("Invert Y-Axis", s.invertY) { on ->
                        onUpdateSettings { it.copy(invertY = on) }
                    }
                    SettingSwitch("Aim Assist", s.aimAssistEnabled) { on ->
                        onUpdateSettings { it.copy(aimAssistEnabled = on) }
                    }
                    SettingSwitch("Gyroscope Aiming", s.gyroEnabled) { on ->
                        onUpdateSettings { it.copy(gyroEnabled = on) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(
                            onClick = onOpenHudEditor,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidObsidian),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("edit_hud_button")
                        ) {
                            Text("EDIT HUD LAYOUT", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = { showResetConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger.copy(alpha = 0.25f), contentColor = CrimsonDanger),
                            border = BorderStroke(1.dp, CrimsonDanger),
                            modifier = Modifier.height(44.dp).testTag("reset_save_button")
                        ) {
                            Text("RESET DATA", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = SlateArmor,
            title = { Text("Reset All Save Data?", color = CrimsonDanger, style = MaterialTheme.typography.headlineMedium) },
            text = { Text("This permanently clears your level, rank RP, coins, gems, unlocked skins, Battle Pass progress, and career stats.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showResetConfirm = false; onResetSaveData() }) {
                    Text("RESET EVERYTHING", color = CrimsonDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("CANCEL", color = NeonCyan)
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = SlateArmor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            content()
        }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, readout: String, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(readout, style = MaterialTheme.typography.labelMedium, color = NeonCyan)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan, inactiveTrackColor = SlateBorder)
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = VoidObsidian, checkedTrackColor = NeonCyan)
        )
    }
}

// ============================================================================
// INTERACTIVE DRAG-AND-SCALE EDIT HUD SCREEN
// ============================================================================

@Composable
fun HudEditorScreen(
    initialLayout: Map<String, HudButtonLayout>,
    onSave: (Map<String, HudButtonLayout>) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    val editable = remember {
        mutableStateMapOf<String, HudButtonLayout>().apply {
            putAll(GameSettings.defaultHudLayout())
            putAll(initialLayout)
        }
    }
    var selectedKey by remember { mutableStateOf("fire") }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val wPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val hPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        // Tactical Blueprint Grid Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48f
            var x = 0f
            while (x < size.width) {
                drawLine(SlateBorder.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height))
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(SlateBorder.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y))
                y += step
            }
        }

        // Top Editor Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .background(SlateArmor.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("DRAG BUTTONS TO REPOSITION · SELECTED: ${selectedKey.uppercase()}", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            val selScale = editable[selectedKey]?.scale ?: 1f
            Slider(
                value = ((selScale - 0.7f) / 0.8f).coerceIn(0f, 1f),
                onValueChange = { v ->
                    val cur = editable[selectedKey] ?: return@Slider
                    editable[selectedKey] = cur.copy(scale = 0.7f + v * 0.8f)
                },
                modifier = Modifier.width(100.dp)
            )
            Button(
                onClick = {
                    editable.clear()
                    editable.putAll(GameSettings.defaultHudLayout())
                    onReset()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateElevated, contentColor = TextPrimary),
                modifier = Modifier.height(36.dp)
            ) { Text("RESET", style = MaterialTheme.typography.labelSmall) }
            Button(
                onClick = { onSave(editable.toMap()); onCancel() },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidObsidian),
                modifier = Modifier.height(36.dp).testTag("save_hud_button")
            ) { Text("SAVE", style = MaterialTheme.typography.labelSmall) }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = PlasmaMagenta, contentColor = TextPrimary),
                modifier = Modifier.height(36.dp)
            ) { Text("EXIT", style = MaterialTheme.typography.labelSmall) }
        }

        // Draggable HUD Buttons
        editable.entries.forEach { (key, lay) ->
            val btnSizeDp = (58f * lay.scale).dp
            val btnSizePx = with(density) { btnSizeDp.toPx() }
            val isSelected = selectedKey == key

            Surface(
                color = if (key == "fire") PlasmaMagenta.copy(alpha = 0.45f) else SlateArmor.copy(alpha = 0.82f),
                shape = CircleShape,
                border = BorderStroke(2.dp, if (isSelected) NeonCyan else SlateBorder),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (lay.xFrac * wPx - btnSizePx * 0.5f).roundToInt(),
                            (lay.yFrac * hPx - btnSizePx * 0.5f).roundToInt()
                        )
                    }
                    .size(btnSizeDp)
                    .pointerInput(key) {
                        detectDragGestures(
                            onDragStart = { selectedKey = key },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val cur = editable[key] ?: return@detectDragGestures
                                val nx = (cur.xFrac + dragAmount.x / wPx).coerceIn(0.06f, 0.94f)
                                val ny = (cur.yFrac + dragAmount.y / hPx).coerceIn(0.16f, 0.92f)
                                editable[key] = cur.copy(xFrac = nx, yFrac = ny)
                            }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(key.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
    }
}
