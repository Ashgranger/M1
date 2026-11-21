package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.game.progression.ProgressionCatalog
import com.example.game.render.GameSurfaceView
import com.example.ui.screens.BattlePassScreen
import com.example.ui.screens.ChallengesScreen
import com.example.ui.screens.HudEditorScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.LuckySpinModal
import com.example.ui.screens.MatchHudOverlay
import com.example.ui.screens.PauseModal
import com.example.ui.screens.PostMatchOverlay
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShopScreen
import com.example.ui.screens.StatsAndMasteryScreen
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SlateArmor
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import com.example.ui.theme.ZoneZeroTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.ZoneZeroViewModel

class MainActivity : ComponentActivity(), SensorEventListener {

    private val vm: ZoneZeroViewModel by viewModels()
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var mapToggleCallback: (() -> Unit)? = null
    private var pauseToggleCallback: (() -> Unit)? = null
    private var lastMouseX = Float.NaN
    private var lastMouseY = Float.NaN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureImmersiveBars()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            ZoneZeroTheme {
                ZoneZeroApp(
                    vm = vm,
                    onRegisterShortcuts = { onMap, onPause ->
                        mapToggleCallback = onMap
                        pauseToggleCallback = onPause
                    }
                )
            }
        }
    }

    private fun configureImmersiveBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        configureImmersiveBars()
        vm.audio.isPaused = false
        gyroSensor?.let { s ->
            sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        vm.setMatchPaused(true)
        vm.activeEngine.value?.input?.clearAll()
        sensorManager?.unregisterListener(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            vm.activeEngine.value?.input?.clearAll()
        } else {
            configureImmersiveBars()
        }
    }

    // Optional Gyroscope Aiming
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_GYROSCOPE) return
        if (!vm.saveData.value.settings.gyroEnabled) return
        val engine = vm.activeEngine.value ?: return
        if (vm.screen.value != AppScreen.IN_MATCH || engine.paused) return
        // Landscape sensor axes -> yaw & pitch delta
        engine.input.addLookDelta(-event.values[0] * 0.025f, event.values[1] * 0.025f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // Hardware Keyboard Controls (WASD, Space, Shift, C, R, E, 1-5, G, Q, M, F, Esc)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val engine = vm.activeEngine.value
        if (vm.screen.value == AppScreen.IN_MATCH && engine != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_W -> { engine.input.keyW = true; return true }
                KeyEvent.KEYCODE_S -> { engine.input.keyS = true; return true }
                KeyEvent.KEYCODE_A -> { engine.input.keyA = true; return true }
                KeyEvent.KEYCODE_D -> { engine.input.keyD = true; return true }
                KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> { engine.input.sprintHeld = true; return true }
                KeyEvent.KEYCODE_SPACE -> { engine.input.jumpPressed.set(true); return true }
                KeyEvent.KEYCODE_C -> { engine.input.crouchPressed.set(true); return true }
                KeyEvent.KEYCODE_R -> { engine.input.reloadPressed.set(true); return true }
                KeyEvent.KEYCODE_E -> { engine.input.pickupPressed.set(true); return true }
                KeyEvent.KEYCODE_G -> { engine.input.grenadePressed.set(true); return true }
                KeyEvent.KEYCODE_Q -> { engine.input.dropPressed.set(true); return true }
                KeyEvent.KEYCODE_F -> { engine.input.vehicleOrMeleePressed.set(true); return true }
                KeyEvent.KEYCODE_1 -> { engine.input.selectSlot.set(0); return true }
                KeyEvent.KEYCODE_2 -> { engine.input.selectSlot.set(1); return true }
                KeyEvent.KEYCODE_3 -> { engine.input.selectSlot.set(2); return true }
                KeyEvent.KEYCODE_4 -> { engine.input.selectSlot.set(3); return true }
                KeyEvent.KEYCODE_5 -> { engine.input.selectSlot.set(4); return true }
                KeyEvent.KEYCODE_M -> { mapToggleCallback?.invoke(); return true }
                KeyEvent.KEYCODE_ESCAPE -> { pauseToggleCallback?.invoke(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val engine = vm.activeEngine.value
        if (vm.screen.value == AppScreen.IN_MATCH && engine != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_W -> { engine.input.keyW = false; return true }
                KeyEvent.KEYCODE_S -> { engine.input.keyS = false; return true }
                KeyEvent.KEYCODE_A -> { engine.input.keyA = false; return true }
                KeyEvent.KEYCODE_D -> { engine.input.keyD = false; return true }
                KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> { engine.input.sprintHeld = false; return true }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    // Hardware Mouse Look & Left/Right Click Fire/ADS
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        val engine = vm.activeEngine.value
        if (event != null && vm.screen.value == AppScreen.IN_MATCH && engine != null && !engine.paused) {
            if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE || event.actionMasked == MotionEvent.ACTION_MOVE) {
                if (!lastMouseX.isNaN()) {
                    val dx = event.x - lastMouseX
                    val dy = event.y - lastMouseY
                    engine.input.addLookDelta(dx * 0.004f, -dy * 0.004f)
                }
                lastMouseX = event.x
                lastMouseY = event.y
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}

@Composable
private fun ZoneZeroApp(
    vm: ZoneZeroViewModel,
    onRegisterShortcuts: (onMap: () -> Unit, onPause: () -> Unit) -> Unit
) {
    val save by vm.saveData.collectAsState()
    val screen by vm.screen.collectAsState()
    val engine by vm.activeEngine.collectAsState()
    val postRewards by vm.postMatchRewards.collectAsState()
    val toast by vm.toast.collectAsState()
    val showSpin by vm.showLuckySpin.collectAsState()
    val lastSpin by vm.lastSpinResult.collectAsState()
    val renderError by vm.renderError.collectAsState()
    val matchKey by vm.matchInstanceKey.collectAsState()

    var showPause by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }

    onRegisterShortcuts(
        { showMap = !showMap },
        {
            showPause = !showPause
            vm.setMatchPaused(showPause)
        }
    )

    // Handle system Back button cleanly across all secondary screens
    BackHandler(enabled = screen != AppScreen.LOBBY) {
        when (screen) {
            AppScreen.IN_MATCH -> {
                if (showMap) {
                    showMap = false
                } else {
                    showPause = !showPause
                    vm.setMatchPaused(showPause)
                }
            }
            AppScreen.HUD_EDITOR -> vm.navigate(AppScreen.SETTINGS)
            else -> vm.navigate(AppScreen.LOBBY)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(VoidObsidian)) {
        when (screen) {
            AppScreen.LOBBY -> LobbyScreen(
                save = save,
                onNavigate = vm::navigate,
                onDeploy = {
                    showPause = false; showMap = false
                    vm.startMatch()
                },
                onOpenSpin = vm::openLuckySpin,
                onEquipCosmetic = { id ->
                    ProgressionCatalog.COSMETIC_BY_ID[id]?.let { vm.equipCosmetic(it) }
                }
            )
            AppScreen.SHOP -> ShopScreen(
                save = save,
                onBack = { vm.navigate(AppScreen.LOBBY) },
                onBuyOrEquip = vm::buyOrEquipCosmetic
            )
            AppScreen.BATTLE_PASS -> BattlePassScreen(
                save = save,
                onBack = { vm.navigate(AppScreen.LOBBY) },
                onUnlockPremium = vm::unlockPremiumBattlePass,
                onClaimTier = vm::claimBattlePassTier
            )
            AppScreen.CHALLENGES -> ChallengesScreen(
                save = save,
                onBack = { vm.navigate(AppScreen.LOBBY) },
                onClaimChallenge = vm::claimChallenge,
                onClaimLoginDay = vm::claimLoginDay,
                onClaimAchievement = vm::claimAchievement
            )
            AppScreen.STATS -> StatsAndMasteryScreen(
                save = save,
                onBack = { vm.navigate(AppScreen.LOBBY) },
                onClaimMastery = vm::claimMasteryReward
            )
            AppScreen.SETTINGS -> SettingsScreen(
                save = save,
                onBack = { vm.navigate(AppScreen.LOBBY) },
                onUpdateSettings = vm::updateSettings,
                onOpenHudEditor = { vm.navigate(AppScreen.HUD_EDITOR) },
                onResetSaveData = vm::resetAllSaveData
            )
            AppScreen.HUD_EDITOR -> HudEditorScreen(
                initialLayout = save.settings.hudLayout,
                onSave = vm::saveHudLayout,
                onReset = vm::resetHudLayout,
                onCancel = { vm.navigate(AppScreen.SETTINGS) }
            )
            AppScreen.IN_MATCH -> {
                val active = engine
                if (renderError != null) {
                    ErrorRecoveryScreen(
                        message = renderError ?: "Graphics context lost",
                        onRetry = { vm.playAgain() },
                        onLobby = { vm.returnToLobby() }
                    )
                } else if (active != null) {
                    key(matchKey) {
                        var glViewRef by remember { mutableStateOf<GameSurfaceView?>(null) }
                        DisposableEffect(matchKey) {
                            onDispose { glViewRef?.onPause() }
                        }
                        AndroidView(
                            factory = { ctx ->
                                GameSurfaceView(ctx, active, vm::reportRenderError).also { glViewRef = it }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    MatchHudOverlay(
                        engine = active,
                        settings = save.settings,
                        showFullMap = showMap,
                        onToggleMap = { showMap = !showMap },
                        onPause = {
                            showPause = true
                            vm.setMatchPaused(true)
                        }
                    )

                    if (showPause && postRewards == null) {
                        PauseModal(
                            onResume = {
                                showPause = false
                                vm.setMatchPaused(false)
                            },
                            onSettings = {
                                showPause = false
                                vm.navigate(AppScreen.SETTINGS)
                            },
                            onQuitLobby = {
                                showPause = false
                                vm.returnToLobby()
                            }
                        )
                    }

                    postRewards?.let { rew ->
                        PostMatchOverlay(
                            rewards = rew,
                            onPlayAgain = {
                                showPause = false; showMap = false
                                vm.playAgain()
                            },
                            onReturnLobby = {
                                showPause = false; showMap = false
                                vm.returnToLobby()
                            }
                        )
                    }
                }
            }
        }

        // 4-Hour Lucky Spin Modal
        if (showSpin) {
            LuckySpinModal(
                save = save,
                lastResult = lastSpin,
                onSpin = vm::performLuckySpin,
                onDismiss = vm::closeLuckySpin
            )
        }

        // Achievement / Progression Toast Popup
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        ) {
            toast?.let { t ->
                Surface(
                    color = SlateArmor.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, LegendaryAmber)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(t.title, style = MaterialTheme.typography.labelLarge, color = LegendaryAmber)
                        Text(t.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorRecoveryScreen(message: String, onRetry: () -> Unit, onLobby: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(VoidObsidian), contentAlignment = Alignment.Center) {
        Surface(color = SlateArmor, shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, CrimsonDanger), modifier = Modifier.width(380.dp)) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RENDERER RECOVERING", style = MaterialTheme.typography.headlineMedium, color = CrimsonDanger)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidObsidian)) {
                    Text("RELAUNCH MATCH", style = MaterialTheme.typography.labelLarge)
                }
                Button(onClick = onLobby, colors = ButtonDefaults.buttonColors(containerColor = VoidObsidian, contentColor = TextPrimary)) {
                    Text("RETURN TO LOBBY", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
