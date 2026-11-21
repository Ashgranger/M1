package com.example.game.engine

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// ============================================================================
// SECTION: INPUT — Multi-Touch, Gyroscope & Hardware Keyboard/Mouse Bus
// ============================================================================

class InputState {
    // Continuous movement (-1..1)
    @Volatile var moveX: Float = 0f
    @Volatile var moveY: Float = 0f // +1 forward, -1 backward

    // Look delta accumulator (radians, consumed each simulation tick)
    private val lookLock = Any()
    private var accumYaw: Float = 0f
    private var accumPitch: Float = 0f

    // Continuous holds
    @Volatile var fireHeld: Boolean = false
    @Volatile var aimHeld: Boolean = false
    @Volatile var sprintHeld: Boolean = false
    @Volatile var sprintLocked: Boolean = false

    // Keyboard state
    @Volatile var keyW: Boolean = false
    @Volatile var keyS: Boolean = false
    @Volatile var keyA: Boolean = false
    @Volatile var keyD: Boolean = false

    // Edge-triggered actions
    val jumpPressed = AtomicBoolean(false)
    val crouchPressed = AtomicBoolean(false)
    val reloadPressed = AtomicBoolean(false)
    val pickupPressed = AtomicBoolean(false)
    val grenadePressed = AtomicBoolean(false)
    val cycleGrenadePressed = AtomicBoolean(false)
    val healPressed = AtomicBoolean(false)
    val dropPressed = AtomicBoolean(false)
    val vehicleOrMeleePressed = AtomicBoolean(false)
    val flashlightPressed = AtomicBoolean(false)
    val planeJumpPressed = AtomicBoolean(false)
    val spectateNextPressed = AtomicBoolean(false)
    val selectSlot = AtomicInteger(-1)

    fun addLookDelta(dYaw: Float, dPitch: Float) {
        synchronized(lookLock) {
            accumYaw += dYaw
            accumPitch += dPitch
        }
    }

    fun consumeLookDelta(out: FloatArray) {
        synchronized(lookLock) {
            out[0] = accumYaw
            out[1] = accumPitch
            accumYaw = 0f
            accumPitch = 0f
        }
    }

    fun effectiveMoveX(): Float {
        val kb = (if (keyD) 1f else 0f) - (if (keyA) 1f else 0f)
        return (moveX + kb).coerceIn(-1f, 1f)
    }

    fun effectiveMoveY(): Float {
        val kb = (if (keyW) 1f else 0f) - (if (keyS) 1f else 0f)
        return (moveY + kb).coerceIn(-1f, 1f)
    }

    fun clearAll() {
        moveX = 0f; moveY = 0f
        fireHeld = false; aimHeld = false; sprintHeld = false; sprintLocked = false
        keyW = false; keyS = false; keyA = false; keyD = false
        synchronized(lookLock) { accumYaw = 0f; accumPitch = 0f }
    }
}
