package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.game.config.Biome
import com.example.game.config.WeaponClass
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// SECTION: AUDIO — Real-Time Procedural PCM Synth & 3D Positional Audio Mixer
// ============================================================================

class ProceduralAudioEngine {

    private val sampleRate = 22050
    private val chunkSamples = 512

    @Volatile var sfxVolume: Float = 0.85f
    @Volatile var musicVolume: Float = 0.45f
    @Volatile var isInMatch: Boolean = false
    @Volatile var inPlane: Boolean = false
    @Volatile var inParachute: Boolean = false
    @Volatile var inStorm: Boolean = false
    @Volatile var stormStage: Int = 1
    @Volatile var isPaused: Boolean = false

    private data class Voice(
        val type: VoiceType,
        val durationSec: Float,
        val baseFreq: Float,
        val endFreq: Float,
        val noiseMix: Float,
        val gain: Float,
        val pan: Float, // -1 left .. +1 right
        var elapsed: Float = 0f,
        var phase: Float = 0f
    )

    private enum class VoiceType {
        SHOT, FOOTSTEP, RELOAD, HIT, HEADSHOT, PICKUP, EXPLOSION, UI_CLICK, STREAK, SIREN
    }

    private val pendingVoices = ConcurrentLinkedQueue<Voice>()
    private val activeVoices = ArrayList<Voice>(32)
    @Volatile private var running = false
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private var noiseState = 123456789
    private var musicTime = 0f
    private var ambiencePhase = 0f

    fun start() {
        if (running) return
        running = true
        thread = Thread({ runMixerLoop() }, "ZoneZeroAudio").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try {
            thread?.join(250)
        } catch (_: Throwable) {
        }
        thread = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        audioTrack = null
    }

    // 3D positional helper (listener at x,z facing yawRad)
    private fun compute3dPanAndGain(
        listenerX: Float, listenerZ: Float, listenerYaw: Float,
        srcX: Float, srcZ: Float, maxDist: Float
    ): Pair<Float, Float> {
        val dx = srcX - listenerX
        val dz = srcZ - listenerZ
        val dist = sqrt(dx * dx + dz * dz)
        if (dist >= maxDist) return 0f to 0f
        val atten = (1f - dist / maxDist).let { it * it }
        if (dist < 1.5f) return 0f to atten
        val rightX = cos(listenerYaw)
        val rightZ = sin(listenerYaw)
        val dot = (dx * rightX + dz * rightZ) / dist
        return dot.coerceIn(-1f, 1f) to atten
    }

    fun playWeaponShot(
        weapon: WeaponClass,
        silenced: Boolean,
        listenerX: Float, listenerZ: Float, listenerYaw: Float,
        srcX: Float, srcZ: Float
    ) {
        val maxDist = if (silenced) weapon.soundRadius * 0.45f else weapon.soundRadius
        val (pan, atten) = compute3dPanAndGain(listenerX, listenerZ, listenerYaw, srcX, srcZ, maxDist)
        if (atten <= 0.02f) return

        val (freq, endFreq, dur, noise, gain) = when (weapon) {
            WeaponClass.PISTOL -> Quints(420f, 120f, 0.11f, 0.55f, 0.42f)
            WeaponClass.REVOLVER -> Quints(260f, 75f, 0.22f, 0.65f, 0.65f)
            WeaponClass.SMG -> Quints(540f, 180f, 0.08f, 0.60f, 0.36f)
            WeaponClass.ASSAULT_RIFLE -> Quints(310f, 95f, 0.14f, 0.72f, 0.50f)
            WeaponClass.LMG -> Quints(240f, 80f, 0.16f, 0.78f, 0.56f)
            WeaponClass.SHOTGUN -> Quints(180f, 48f, 0.28f, 0.88f, 0.75f)
            WeaponClass.SNIPER -> Quints(220f, 52f, 0.34f, 0.80f, 0.80f)
            WeaponClass.MARKSMAN -> Quints(340f, 90f, 0.18f, 0.68f, 0.55f)
            WeaponClass.CROSSBOW -> Quints(680f, 240f, 0.12f, 0.20f, 0.32f)
            WeaponClass.ROCKET_LAUNCHER -> Quints(130f, 45f, 0.45f, 0.90f, 0.82f)
        }
        val silMult = if (silenced) 0.42f else 1f
        enqueue(
            Voice(
                type = VoiceType.SHOT,
                durationSec = if (silenced) dur * 0.65f else dur,
                baseFreq = if (silenced) freq * 1.45f else freq,
                endFreq = endFreq,
                noiseMix = if (silenced) noise * 0.5f else noise,
                gain = gain * atten * silMult,
                pan = pan
            )
        )
    }

    fun playExplosion(listenerX: Float, listenerZ: Float, listenerYaw: Float, srcX: Float, srcZ: Float) {
        val (pan, atten) = compute3dPanAndGain(listenerX, listenerZ, listenerYaw, srcX, srcZ, 300f)
        if (atten <= 0.02f) return
        enqueue(Voice(VoiceType.EXPLOSION, 0.75f, 95f, 26f, 0.85f, 0.92f * atten, pan))
    }

    fun playFootstep(biome: Biome, indoor: Boolean) {
        val freq = if (indoor) 230f else 160f * biome.footstepPitch
        val noise = if (biome == Biome.WATER || biome == Biome.SWAMP) 0.85f else 0.55f
        enqueue(Voice(VoiceType.FOOTSTEP, 0.065f, freq, freq * 0.55f, noise, 0.16f, 0f))
    }

    fun playReload() {
        enqueue(Voice(VoiceType.RELOAD, 0.14f, 620f, 880f, 0.25f, 0.28f, 0f))
    }

    fun playHit(headshot: Boolean) {
        if (headshot) {
            enqueue(Voice(VoiceType.HEADSHOT, 0.18f, 1240f, 1780f, 0.08f, 0.52f, 0f))
        } else {
            enqueue(Voice(VoiceType.HIT, 0.07f, 880f, 520f, 0.20f, 0.34f, 0f))
        }
    }

    fun playPickup() {
        enqueue(Voice(VoiceType.PICKUP, 0.14f, 520f, 960f, 0.05f, 0.32f, 0f))
    }

    fun playUiClick() {
        enqueue(Voice(VoiceType.UI_CLICK, 0.045f, 760f, 1120f, 0.05f, 0.24f, 0f))
    }

    fun playKillStreak(streakCount: Int) {
        val root = 440f + (streakCount.coerceAtMost(5) * 65f)
        enqueue(Voice(VoiceType.STREAK, 0.45f, root, root * 1.5f, 0.05f, 0.50f, 0f))
    }

    fun playAirdropSiren() {
        enqueue(Voice(VoiceType.SIREN, 0.85f, 520f, 780f, 0.05f, 0.42f, 0f))
    }

    private fun enqueue(voice: Voice) {
        if (pendingVoices.size < 28) pendingVoices.add(voice)
    }

    private fun nextNoise(): Float {
        var x = noiseState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noiseState = x
        return (x and 0xFFFF) / 32768f - 1f
    }

    private fun runMixerLoop() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(chunkSamples * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = track
            track.play()

            val pcm = ShortArray(chunkSamples * 2)
            val dt = 1f / sampleRate

            // Adaptive synth notes (pentatonic dark-neon cyber pulse)
            val bassNotes = floatArrayOf(55f, 55f, 65.4f, 49f, 55f, 73.4f, 65.4f, 49f)
            val leadNotes = floatArrayOf(220f, 261.6f, 329.6f, 293.7f, 220f, 392f, 329.6f, 261.6f)

            while (running) {
                if (isPaused) {
                    Thread.sleep(30)
                    continue
                }

                while (true) {
                    val v = pendingVoices.poll() ?: break
                    if (activeVoices.size < 24) activeVoices.add(v)
                }

                val sfxVol = sfxVolume
                val musVol = musicVolume
                val stageIntensity = if (!isInMatch) 0.45f else (0.35f + (stormStage.coerceIn(1, 7) / 7f) * 0.65f)
                val bpm = if (!isInMatch) 96f else (106f + stormStage * 7f)
                val beatDur = 60f / bpm

                for (i in 0 until chunkSamples) {
                    var left = 0f
                    var right = 0f

                    // 1. Adaptive background music loop
                    if (musVol > 0.01f) {
                        musicTime += dt
                        val stepIndex = ((musicTime / (beatDur * 0.5f)).toInt()) % bassNotes.size
                        val stepPhase = (musicTime % (beatDur * 0.5f)) / (beatDur * 0.5f)
                        val env = exp(-stepPhase * 4.5f)
                        val bass = sin(2.0 * PI * bassNotes[stepIndex] * musicTime).toFloat() * env * 0.22f
                        val pulse = if (stageIntensity > 0.65f) {
                            sin(2.0 * PI * leadNotes[stepIndex] * musicTime).toFloat() * exp(-stepPhase * 8f) * 0.10f * stageIntensity
                        } else 0f
                        val m = (bass + pulse) * musVol
                        left += m
                        right += m
                    }

                    // 2. Environmental ambience (plane / parachute wind / storm crackle)
                    if (sfxVol > 0.01f && isInMatch) {
                        ambiencePhase += dt
                        if (inPlane) {
                            val prop = (sin(2.0 * PI * 74.0 * ambiencePhase).toFloat() * 0.5f + nextNoise() * 0.35f) * 0.14f * sfxVol
                            left += prop; right += prop
                        } else if (inParachute) {
                            val wind = nextNoise() * 0.11f * sfxVol
                            left += wind; right += wind
                        }
                        if (inStorm) {
                            val hum = (sin(2.0 * PI * 112.0 * ambiencePhase).toFloat() * 0.5f + nextNoise() * 0.45f) * 0.15f * sfxVol
                            left += hum; right += hum
                        }
                    }

                    // 3. Active 3D SFX voices
                    var vIdx = activeVoices.size - 1
                    while (vIdx >= 0) {
                        val v = activeVoices[vIdx]
                        v.elapsed += dt
                        if (v.elapsed >= v.durationSec) {
                            activeVoices.removeAt(vIdx)
                            vIdx--
                            continue
                        }
                        val t = v.elapsed / v.durationSec
                        val freq = v.baseFreq + (v.endFreq - v.baseFreq) * t
                        v.phase += (2.0 * PI * freq * dt).toFloat()
                        val env = when (v.type) {
                            VoiceType.SIREN -> sin(t * PI).toFloat()
                            VoiceType.STREAK -> (1f - t) * (0.6f + 0.4f * sin(t * 18f))
                            else -> exp(-t * 5.2f)
                        }
                        val tone = sin(v.phase)
                        val raw = (tone * (1f - v.noiseMix) + nextNoise() * v.noiseMix) * env * v.gain * sfxVol
                        val lGain = sqrt((1f - v.pan) * 0.5f)
                        val rGain = sqrt((1f + v.pan) * 0.5f)
                        left += raw * lGain
                        right += raw * rGain
                        vIdx--
                    }

                    val cl = left.coerceIn(-0.95f, 0.95f)
                    val cr = right.coerceIn(-0.95f, 0.95f)
                    pcm[i * 2] = (cl * 32767f).toInt().toShort()
                    pcm[i * 2 + 1] = (cr * 32767f).toInt().toShort()
                }

                track.write(pcm, 0, pcm.size)
            }
        } catch (_: Throwable) {
            // Audio fails safely without crashing gameplay
        }
    }

    private data class Quints(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float)
}
