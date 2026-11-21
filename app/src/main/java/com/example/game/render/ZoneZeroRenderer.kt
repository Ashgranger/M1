package com.example.game.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.config.WeaponClass
import com.example.game.config.WorldConfig
import com.example.game.engine.MatchEngine
import com.example.game.engine.ScopeMode
import com.example.game.engine.ScreenDamageNumber
import com.example.game.entities.Actor
import com.example.game.entities.DropPhase
import com.example.game.world.InstancedProp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// SECTION: 3D RENDERER — Articulated Tactical Soldiers, GTA Roads & PUBG World
// ============================================================================

class ZoneZeroRenderer(
    private val engine: MatchEngine,
    private val onRenderError: (Throwable) -> Unit
) : GLSurfaceView.Renderer {

    private var litProgram = 0
    private var instProgram = 0
    private var skyProgram = 0
    private var fxProgram = 0

    private lateinit var terrainMesh: GpuMesh
    private lateinit var roadMesh: GpuMesh
    private lateinit var cubeMesh: GpuMesh
    private lateinit var sphereMesh: GpuMesh
    private lateinit var limbMesh: GpuMesh
    private lateinit var coneMesh: GpuMesh
    private lateinit var cylinderMesh: GpuMesh
    private lateinit var waterMesh: GpuMesh

    private var treeTrunkInstVbo = 0
    private var treeCanopyInstVbo = 0
    private var rockInstVbo = 0
    private val maxInstances = 720
    private val instanceFloatArray = FloatArray(maxInstances * 10)
    private val instanceBuf: FloatBuffer =
        ByteBuffer.allocateDirect(maxInstances * 10 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private val projMat = FloatArray(16)
    private val viewMat = FloatArray(16)
    private val viewProjMat = FloatArray(16)
    private val viewProjNoTrans = FloatArray(16)
    private val modelMat = FloatArray(16)
    private val mvpMat = FloatArray(16)
    private val projVec = FloatArray(4)

    private var viewportW = 1280
    private var viewportH = 720
    private var lastFrameNs = System.nanoTime()
    private var elapsedSec = 0f
    private var camX = 0f; private var camY = 10f; private var camZ = 0f
    private var camFwdX = 0f; private var camFwdY = 0f; private var camFwdZ = 1f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glCullFace(GLES30.GL_BACK)

            litProgram = GlShaders.compileProgram(GlShaders.LIT_VERT, GlShaders.LIT_FRAG)
            instProgram = GlShaders.compileProgram(GlShaders.INSTANCED_VERT, GlShaders.LIT_FRAG)
            skyProgram = GlShaders.compileProgram(GlShaders.SKY_VERT, GlShaders.SKY_FRAG)
            fxProgram = GlShaders.compileProgram(GlShaders.FX_VERT, GlShaders.FX_FRAG)

            terrainMesh = MeshFactory.createTerrainMesh(engine.world.terrain, 112)
            roadMesh = MeshFactory.createRoadNetworkMesh(engine.world.terrain, engine.world.roadNetwork)
            cubeMesh = MeshFactory.createUnitCube()
            sphereMesh = MeshFactory.createSphereMesh(7, 10)
            limbMesh = MeshFactory.createLimbCylinderMesh(9, 0.88f)
            coneMesh = MeshFactory.createConeMesh(7)
            cylinderMesh = MeshFactory.createOpenCylinder(48)
            waterMesh = MeshFactory.createWaterPlane()

            val vbos = IntArray(3)
            GLES30.glGenBuffers(3, vbos, 0)
            treeTrunkInstVbo = vbos[0]
            treeCanopyInstVbo = vbos[1]
            rockInstVbo = vbos[2]
            lastFrameNs = System.nanoTime()
        } catch (t: Throwable) {
            onRenderError(t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportW, viewportH)
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            val now = System.nanoTime()
            val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, WorldConfig.MAX_DELTA_TIME)
            lastFrameNs = now
            elapsedSec += dt

            val projectedDmg = projectDamageNumbers()
            engine.step(dt, projectedDmg)

            val quality = engine.settings.qualityEnum()
            val tod = engine.timeOfDay
            val obs = engine.getObservedActor()

            val scopeMode = engine.hudSnapshot.scopeMode
            val fov = when (scopeMode) {
                ScopeMode.SNIPER -> 20f
                ScopeMode.RED_DOT -> 44f
                ScopeMode.NONE -> if (obs.isAiming) 50f else if (obs.dropPhase == DropPhase.IN_PLANE) 68f else 60f
            }
            Matrix.perspectiveM(projMat, 0, fov, viewportW.toFloat() / viewportH.toFloat(), 0.25f, quality.drawDistance + 240f)

            val yaw = obs.yaw
            val pitch = (obs.pitch + if (obs.isHuman) engine.recoilPitchOffset else 0f).coerceIn(-1.2f, 1.2f)
            val lookDx = sin(yaw) * cos(pitch)
            val lookDy = sin(pitch)
            val lookDz = cos(yaw) * cos(pitch)
            val rightX = cos(yaw)
            val rightZ = -sin(yaw)

            val shakeX = if (engine.cameraShake > 0.01f) (sin(elapsedSec * 73f) * engine.cameraShake * 0.22f) else 0f
            val shakeY = if (engine.cameraShake > 0.01f) (cos(elapsedSec * 91f) * engine.cameraShake * 0.18f) else 0f

            val eyeHeight = if (obs.isCrouching) 1.08f else 1.60f
            val anchorX = obs.x
            val anchorY = obs.y + eyeHeight
            val anchorZ = obs.z

            if (obs.dropPhase == DropPhase.IN_PLANE) {
                camX = engine.planeX - sin(engine.planeYaw) * 26f
                camY = WorldConfig.PLANE_ALTITUDE + 10f
                camZ = engine.planeZ - cos(engine.planeYaw) * 26f
                Matrix.setLookAtM(viewMat, 0, camX, camY, camZ, engine.planeX, WorldConfig.PLANE_ALTITUDE - 12f, engine.planeZ, 0f, 1f, 0f)
                camFwdX = sin(engine.planeYaw); camFwdY = -0.35f; camFwdZ = cos(engine.planeYaw)
            } else if (scopeMode != ScopeMode.NONE) {
                camX = anchorX + lookDx * 0.35f
                camY = anchorY + 0.05f
                camZ = anchorZ + lookDz * 0.35f
                Matrix.setLookAtM(viewMat, 0, camX, camY, camZ, camX + lookDx * 40f, camY + lookDy * 40f, camZ + lookDz * 40f, 0f, 1f, 0f)
                camFwdX = lookDx; camFwdY = lookDy; camFwdZ = lookDz
            } else {
                // PUBG / GTA classic over-the-right-shoulder camera with anti-clipping collision
                val desiredBack = when {
                    obs.dropPhase == DropPhase.FREEFALL || obs.dropPhase == DropPhase.PARACHUTE -> 8.4f
                    obs.vehicleId >= 0 -> 9.0f
                    obs.isAiming -> 2.15f
                    else -> 4.1f
                }
                val shoulderOffset = if (obs.vehicleId >= 0 || obs.dropPhase != DropPhase.LANDED) 0f else 0.78f
                val targetCamX = anchorX - lookDx * desiredBack + rightX * shoulderOffset
                val targetCamY = anchorY - lookDy * desiredBack + (if (obs.vehicleId >= 0) 2.2f else 0.45f)
                val targetCamZ = anchorZ - lookDz * desiredBack + rightZ * shoulderOffset

                val rayDx = targetCamX - anchorX
                val rayDy = targetCamY - anchorY
                val rayDz = targetCamZ - anchorZ
                val rayLen = sqrt(rayDx * rayDx + rayDy * rayDy + rayDz * rayDz).coerceAtLeast(0.1f)
                val camHit = engine.world.spatialGrid.raycastWorld(
                    anchorX, anchorY, anchorZ,
                    rayDx / rayLen, rayDy / rayLen, rayDz / rayLen,
                    rayLen, engine.world.terrain
                )
                val safeDist = if (camHit.hit) (camHit.distance - 0.35f).coerceAtLeast(0.55f) else rayLen
                val ratio = safeDist / rayLen
                camX = anchorX + rayDx * ratio + shakeX
                camY = (anchorY + rayDy * ratio + shakeY).coerceAtLeast(engine.world.terrain.getHeight(anchorX, anchorZ) + 0.45f)
                camZ = anchorZ + rayDz * ratio

                val targetX = anchorX + lookDx * 50f + rightX * shoulderOffset * 0.25f
                val targetY = anchorY + lookDy * 50f
                val targetZ = anchorZ + lookDz * 50f + rightZ * shoulderOffset * 0.25f
                Matrix.setLookAtM(viewMat, 0, camX, camY, camZ, targetX, targetY, targetZ, 0f, 1f, 0f)
                camFwdX = lookDx; camFwdY = lookDy; camFwdZ = lookDz
            }

            Matrix.multiplyMM(viewProjMat, 0, projMat, 0, viewMat, 0)
            GLES30.glClearColor(tod.fogColor[0], tod.fogColor[1], tod.fogColor[2], 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

            val sunDir = if (tod.isNight) floatArrayOf(-0.35f, 0.72f, -0.58f) else floatArrayOf(0.58f, 0.72f, 0.36f)
            val flashlightFlag = if (obs.flashlightOn && tod.isNight) 1f else 0f
            val shadowFlag = if (quality.shadowsEnabled) 1f else 0f

            // 1. Sky Gradient Dome
            drawSkyDome(sunDir)

            // 2. Lit Pass: Terrain, Paved Asphalt Roads, Buildings, Streetlamps, Vehicles & Realistic Soldiers
            GLES30.glUseProgram(litProgram)
            bindLightingUniforms(litProgram, sunDir, flashlightFlag, shadowFlag, quality.drawDistance)

            // 2a. Island Terrain & Paved Road Network
            Matrix.setIdentityM(modelMat, 0)
            drawLitMesh(terrainMesh, modelMat, 1f, 1f, 1f, useOverride = 0f)
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            drawLitMesh(roadMesh, modelMat, 1f, 1f, 1f, useOverride = 0f)
            GLES30.glEnable(GLES30.GL_CULL_FACE)

            // 2b. Enterable City Towers, Houses, Bridges, Sidewalks, Crates & Barrels
            val maxBuildSq = (quality.drawDistance * 0.95f).let { it * it }
            val boxes = engine.world.spatialGrid.allColliders
            for (i in 0 until boxes.size) {
                val b = boxes[i]
                if (!b.active) continue
                if (b.kind.name == "TREE_TRUNK" || b.kind.name == "ROCK") continue
                if (!isInFrustumAndRange(b.centerX, b.centerZ, maxBuildSq, 28f)) continue
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, b.centerX, b.centerY, b.centerZ)
                if (b.rampAxis != 0) {
                    val angleDeg = if (b.rampAxis == -1) -34f else 34f
                    Matrix.rotateM(modelMat, 0, angleDeg, 0f, 0f, 1f)
                    Matrix.scaleM(modelMat, 0, b.sizeX * 1.15f, 0.35f, b.sizeZ)
                } else {
                    Matrix.scaleM(modelMat, 0, b.sizeX, b.sizeY, b.sizeZ)
                }
                drawLitMesh(cubeMesh, modelMat, b.colorRgb[0], b.colorRgb[1], b.colorRgb[2], 1f)
            }

            // 2c. Downtown Streetlamps
            val maxLampSq = (quality.drawDistance * 0.55f).let { it * it }
            for (lamp in engine.world.streetLamps) {
                if (!isInFrustumAndRange(lamp[0], lamp[2], maxLampSq, 6f)) continue
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, lamp[0], lamp[1] + 3.2f, lamp[2])
                Matrix.scaleM(modelMat, 0, 0.22f, 6.4f, 0.22f)
                drawLitMesh(limbMesh, modelMat, 0.22f, 0.24f, 0.26f, 1f)

                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, lamp[0], lamp[1] + 6.45f, lamp[2])
                Matrix.scaleM(modelMat, 0, 0.7f, 0.26f, 0.7f)
                val glow = if (tod.isNight) 1.0f else 0.72f
                drawLitMesh(cubeMesh, modelMat, glow, glow * 0.88f, glow * 0.55f, 1f)
            }

            // 2d. Concealment Bushes
            for (i in 0 until engine.world.bushes.size) {
                val b = engine.world.bushes[i]
                if (!isInFrustumAndRange(b.x, b.z, maxLampSq, 4f)) continue
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, b.x, b.y + 0.85f, b.z)
                Matrix.scaleM(modelMat, 0, b.radius * 1.55f, 1.7f, b.radius * 1.55f)
                drawLitMesh(sphereMesh, modelMat, 0.20f, 0.34f, 0.16f, 1f)
            }

            // 2e. Realistic GTA/PUBG SUVs & Off-Road Trucks
            for (v in engine.world.vehicles) {
                if (v.exploded) continue
                if (!isInFrustumAndRange(v.x, v.z, maxBuildSq, 6f)) continue
                drawTacticalVehicle(v.x, v.y, v.z, v.yaw, v.colorRgb)
            }

            // 2f. Military Transport Plane
            if (engine.planeProgress < 1.12f) {
                drawPlane(engine.planeX, WorldConfig.PLANE_ALTITUDE, engine.planeZ, engine.planeYaw)
            }

            // 2g. Airdrop Supply Crates
            for (d in engine.airdrops) {
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, d.x, d.y + 0.9f, d.z)
                Matrix.scaleM(modelMat, 0, 1.85f, 1.85f, 1.85f)
                drawLitMesh(cubeMesh, modelMat, if (d.looted) 0.34f else 0.78f, if (d.looted) 0.34f else 0.14f, if (d.looted) 0.36f else 0.16f, 1f)
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, d.x, d.y + 1.82f, d.z)
                Matrix.scaleM(modelMat, 0, 1.92f, 0.22f, 1.92f)
                drawLitMesh(cubeMesh, modelMat, 0.14f, 0.36f, 0.68f, 1f)
                if (!d.landed) {
                    Matrix.setIdentityM(modelMat, 0)
                    Matrix.translateM(modelMat, 0, d.x, d.y + 5.2f, d.z)
                    Matrix.scaleM(modelMat, 0, 4.8f, 1.4f, 4.8f)
                    drawLitMesh(sphereMesh, modelMat, 0.85f, 0.82f, 0.72f, 1f)
                }
            }

            // 2h. World Loot Pickups
            val maxLootSq = 110f * 110f
            for (i in 0 until engine.world.lootItems.size) {
                val item = engine.world.lootItems[i]
                if (item.pickedUp || !isInFrustumAndRange(item.x, item.z, maxLootSq, 2f)) continue
                val bob = sin(elapsedSec * 3.0f + item.id) * 0.08f
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, item.x, item.y + 0.24f + bob, item.z)
                Matrix.rotateM(modelMat, 0, elapsedSec * 55f + item.id * 17f, 0f, 1f, 0f)
                Matrix.scaleM(modelMat, 0, 0.72f, 0.30f, 0.42f)
                drawLitMesh(cubeMesh, modelMat, item.rarity.rgb[0], item.rarity.rgb[1], item.rarity.rgb[2], 1f)
            }

            // 2i. Realistic Articulated PUBG/GTA Tactical Soldiers (with visible Lv1-3 helmets, vests & backpacks!)
            val maxActorSq = quality.drawDistance * quality.drawDistance
            for (i in 0 until engine.actors.size) {
                val a = engine.actors[i]
                if (a.dropPhase == DropPhase.IN_PLANE || a.vehicleId >= 0) continue
                if (!a.alive && a.deathTimer > 6f) continue
                if (a === obs && scopeMode != ScopeMode.NONE) continue
                if (!isInFrustumAndRange(a.x, a.z, maxActorSq, 3f)) continue
                drawRealisticSoldier(a)
            }

            // 3. Instanced Trees & Rocks (single instanced draw call per mesh with LOD & frustum culling)
            GLES30.glUseProgram(instProgram)
            bindLightingUniforms(instProgram, sunDir, flashlightFlag, shadowFlag, quality.drawDistance)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(instProgram, "uViewProj"), 1, false, viewProjMat, 0)
            drawInstancedProps(limbMesh, treeTrunkInstVbo, engine.world.treeTrunks, quality.drawDistance * 0.85f, quality.treeDensity)
            drawInstancedProps(coneMesh, treeCanopyInstVbo, engine.world.treeCanopies, quality.drawDistance * 0.92f, quality.treeDensity)
            drawInstancedProps(sphereMesh, rockInstVbo, engine.world.rocks, quality.drawDistance * 0.75f, quality.treeDensity)

            // 4. Translucent FX Pass
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            GLES30.glUseProgram(fxProgram)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(fxProgram, "uTime"), elapsedSec)
            GLES30.glUniform3fv(GLES30.glGetUniformLocation(fxProgram, "uFogColor"), 1, tod.fogColor, 0)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(fxProgram, "uFogMaxDist"), quality.drawDistance)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(fxProgram, "uCamPos"), camX, camY, camZ)

            // Ocean & Lakes
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, 0f, WorldConfig.WATER_LEVEL, 0f)
            drawFxMesh(waterMesh, modelMat, 0.08f, 0.30f, 0.46f, 0.86f, mode = 2)

            // Subtle Loot Rarity Glow Beams
            for (i in 0 until engine.world.lootItems.size) {
                val item = engine.world.lootItems[i]
                if (item.pickedUp || !isInFrustumAndRange(item.x, item.z, 85f * 85f, 2f)) continue
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, item.x, item.y, item.z)
                val beamH = 1.6f + item.rarity.ordinal * 0.75f
                Matrix.scaleM(modelMat, 0, 0.11f, beamH, 0.11f)
                drawFxMesh(cylinderMesh, modelMat, item.rarity.rgb[0], item.rarity.rgb[1], item.rarity.rgb[2], 0.28f, mode = 0)
            }

            // Parachute Ground Shadows
            for (a in engine.actors) {
                if (!a.alive || (a.dropPhase != DropPhase.FREEFALL && a.dropPhase != DropPhase.PARACHUTE)) continue
                val gy = engine.world.terrain.getHeight(a.x, a.z)
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, a.x, gy + 0.14f, a.z)
                Matrix.scaleM(modelMat, 0, 2.8f, 0.05f, 2.8f)
                drawFxMesh(sphereMesh, modelMat, 0.02f, 0.03f, 0.04f, 0.42f, mode = 0)
            }

            // Tracers
            for (t in engine.pools.tracers) {
                if (!t.active) continue
                val mx = (t.x1 + t.x2) * 0.5f; val my = (t.y1 + t.y2) * 0.5f; val mz = (t.z1 + t.z2) * 0.5f
                val dx = t.x2 - t.x1; val dy = t.y2 - t.y1; val dz = t.z2 - t.z1
                val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.1f)
                val yawDeg = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat()
                val pitchDeg = -Math.toDegrees(kotlin.math.asin((dy / len).coerceIn(-1f, 1f).toDouble())).toFloat()
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, mx, my, mz)
                Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
                Matrix.rotateM(modelMat, 0, pitchDeg, 1f, 0f, 0f)
                Matrix.scaleM(modelMat, 0, 0.06f, 0.06f, len)
                drawFxMesh(cubeMesh, modelMat, t.r, t.g, t.b, (t.life / t.maxLife).coerceIn(0f, 1f) * 0.9f, mode = 0)
            }

            // Projectiles
            for (p in engine.pools.projectiles) {
                if (!p.active) continue
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, p.x, p.y, p.z)
                Matrix.scaleM(modelMat, 0, 0.28f, 0.28f, 0.65f)
                drawFxMesh(limbMesh, modelMat, 0.95f, 0.58f, 0.14f, 1f, mode = 0)
            }

            // Smoke & Particles
            for (s in engine.pools.smokeClouds) {
                if (!s.active) continue
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, s.x, s.y + 1.8f, s.z)
                Matrix.scaleM(modelMat, 0, s.radius * 1.45f, 5.8f, s.radius * 1.45f)
                drawFxMesh(sphereMesh, modelMat, 0.74f, 0.76f, 0.78f, 0.62f, mode = 0)
            }
            for (p in engine.pools.particles) {
                if (!p.active) continue
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, p.x, p.y, p.z)
                Matrix.scaleM(modelMat, 0, p.size, p.size, p.size)
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f) * (if (p.isSmoke) 0.55f else 0.95f)
                drawFxMesh(if (p.isSmoke) sphereMesh else cubeMesh, modelMat, p.r, p.g, p.b, alpha, mode = 0)
            }

            // Visible Translucent 3D Storm Wall Cylinder
            if (engine.stormRadius > 2f) {
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, engine.stormCenterX, -10f, engine.stormCenterZ)
                Matrix.scaleM(modelMat, 0, engine.stormRadius, 260f, engine.stormRadius)
                drawFxMesh(cylinderMesh, modelMat, 0.28f, 0.48f, 0.96f, 0.42f, mode = 1)
            }

            GLES30.glDepthMask(true)
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glDisable(GLES30.GL_BLEND)
        } catch (t: Throwable) {
            onRenderError(t)
        }
    }

    /** Realistic PUBG/GTA Articulated Tactical Soldier with Visible Lv1-3 Helmets, Vests, Backpacks & Detailed Weapons! */
    private fun drawRealisticSoldier(a: Actor) {
        val yawDeg = Math.toDegrees(a.yaw.toDouble()).toFloat()
        val crouchDrop = if (a.isCrouching) -0.34f else 0f
        val isMoving = sqrt(a.vx * a.vx + a.vz * a.vz) > 0.6f
        val stride = if (a.alive && isMoving) sin(a.animTime) * (if (a.isSprinting) 34f else 22f) else 0f
        val kneeBendL = if (a.alive && isMoving) max(0f, -sin(a.animTime)) * 28f else if (a.isCrouching) 32f else 0f
        val kneeBendR = if (a.alive && isMoving) max(0f, sin(a.animTime)) * 28f else if (a.isCrouching) 32f else 0f
        val deathAngle = if (!a.alive) (a.deathTimer * 260f).coerceAtMost(88f) else 0f

        val jacket = a.skinPrimaryRgb
        val pantsR = (jacket[0] * 0.55f + 0.14f).coerceIn(0.12f, 0.55f)
        val pantsG = (jacket[1] * 0.55f + 0.14f).coerceIn(0.12f, 0.55f)
        val pantsB = (jacket[2] * 0.50f + 0.12f).coerceIn(0.10f, 0.50f)
        val skinR = 0.80f; val skinG = 0.64f; val skinB = 0.52f
        val bootR = 0.14f; val bootG = 0.13f; val bootB = 0.12f

        fun drawPart(
            mesh: GpuMesh,
            ox: Float, oy: Float, oz: Float,
            sx: Float, sy: Float, sz: Float,
            rotX: Float, rotY: Float, rotZ: Float,
            r: Float, g: Float, b: Float
        ) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, a.x, a.y + crouchDrop, a.z)
            Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
            if (deathAngle > 0f) Matrix.rotateM(modelMat, 0, deathAngle, 1f, 0f, 0f)
            Matrix.translateM(modelMat, 0, ox, oy, oz)
            if (rotY != 0f) Matrix.rotateM(modelMat, 0, rotY, 0f, 1f, 0f)
            if (rotX != 0f) Matrix.rotateM(modelMat, 0, rotX, 1f, 0f, 0f)
            if (rotZ != 0f) Matrix.rotateM(modelMat, 0, rotZ, 0f, 0f, 1f)
            Matrix.scaleM(modelMat, 0, sx, sy, sz)
            drawLitMesh(mesh, modelMat, r, g, b, 1f)
        }

        // 1. Articulated Legs (Thighs + Kneepads + Shins + Combat Boots)
        val hipY = 0.84f
        val thighLen = 0.42f
        val shinLen = 0.40f
        // Left Leg
        drawPart(limbMesh, -0.14f, hipY - 0.18f, sin(Math.toRadians(stride.toDouble())).toFloat() * 0.08f, 0.18f, thighLen, 0.19f, stride, 0f, 0f, pantsR, pantsG, pantsB)
        drawPart(sphereMesh, -0.14f, hipY - 0.39f, sin(Math.toRadians(stride.toDouble())).toFloat() * 0.16f, 0.16f, 0.15f, 0.17f, 0f, 0f, 0f, 0.18f, 0.19f, 0.18f) // Tactical kneepad
        drawPart(limbMesh, -0.14f, 0.24f, sin(Math.toRadians((stride - kneeBendL).toDouble())).toFloat() * 0.12f, 0.14f, shinLen, 0.15f, stride - kneeBendL, 0f, 0f, pantsR * 0.85f, pantsG * 0.85f, pantsB * 0.85f)
        drawPart(cubeMesh, -0.14f, 0.07f, 0.04f + sin(Math.toRadians((stride - kneeBendL).toDouble())).toFloat() * 0.16f, 0.15f, 0.14f, 0.28f, 0f, 0f, 0f, bootR, bootG, bootB)
        // Right Leg
        drawPart(limbMesh, 0.14f, hipY - 0.18f, sin(Math.toRadians(-stride.toDouble())).toFloat() * 0.08f, 0.18f, thighLen, 0.19f, -stride, 0f, 0f, pantsR, pantsG, pantsB)
        drawPart(sphereMesh, 0.14f, hipY - 0.39f, sin(Math.toRadians(-stride.toDouble())).toFloat() * 0.16f, 0.16f, 0.15f, 0.17f, 0f, 0f, 0f, 0.18f, 0.19f, 0.18f)
        drawPart(limbMesh, 0.14f, 0.24f, sin(Math.toRadians((-stride - kneeBendR).toDouble())).toFloat() * 0.12f, 0.14f, shinLen, 0.15f, -stride - kneeBendR, 0f, 0f, pantsR * 0.85f, pantsG * 0.85f, pantsB * 0.85f)
        drawPart(cubeMesh, 0.14f, 0.07f, 0.04f + sin(Math.toRadians((-stride - kneeBendR).toDouble())).toFloat() * 0.16f, 0.15f, 0.14f, 0.28f, 0f, 0f, 0f, bootR, bootG, bootB)

        // 2. Pelvis, Utility Belt & Sculpted Torso (Combat Shirt/Jacket)
        drawPart(limbMesh, 0f, 0.88f, 0f, 0.42f, 0.16f, 0.26f, 0f, 0f, 0f, 0.16f, 0.15f, 0.13f) // Tactical belt
        drawPart(limbMesh, 0f, 1.18f, 0f, 0.46f, 0.52f, 0.28f, 0f, 0f, 0f, jacket[0], jacket[1], jacket[2])
        // Rounded Shoulders
        drawPart(sphereMesh, -0.26f, 1.38f, 0f, 0.20f, 0.20f, 0.20f, 0f, 0f, 0f, jacket[0], jacket[1], jacket[2])
        drawPart(sphereMesh, 0.26f, 1.38f, 0f, 0.20f, 0.20f, 0.20f, 0f, 0f, 0f, jacket[0], jacket[1], jacket[2])

        // 3. VISIBLE PUBG BALLISTIC ARMOR VEST (Scales clearly with Lv 1, 2, 3!)
        val vestLvl = a.armorLevel
        if (vestLvl > 0) {
            val (vr, vg, vb) = when (vestLvl) {
                1 -> Triple(0.32f, 0.36f, 0.28f) // Lv1 Olive Police Kevlar
                2 -> Triple(0.46f, 0.42f, 0.32f) // Lv2 Tan Military Plate Carrier
                else -> Triple(0.16f, 0.17f, 0.20f) // Lv3 Heavy Dark-Steel Armored Vest
            }
            val thickness = 0.30f + vestLvl * 0.03f
            drawPart(cubeMesh, 0f, 1.19f, 0f, 0.46f + vestLvl * 0.02f, 0.44f, thickness, 0f, 0f, 0f, vr, vg, vb)
            // Front Magazine Pouches on Plate Carrier
            drawPart(cubeMesh, -0.10f, 1.07f, thickness * 0.54f, 0.10f, 0.14f, 0.06f, 0f, 0f, 0f, vr * 0.8f, vg * 0.8f, vb * 0.8f)
            drawPart(cubeMesh, 0.02f, 1.07f, thickness * 0.54f, 0.10f, 0.14f, 0.06f, 0f, 0f, 0f, vr * 0.8f, vg * 0.8f, vb * 0.8f)
            drawPart(cubeMesh, 0.14f, 1.07f, thickness * 0.54f, 0.10f, 0.14f, 0.06f, 0f, 0f, 0f, vr * 0.8f, vg * 0.8f, vb * 0.8f)
            if (vestLvl == 3) {
                // Heavy shoulder pauldrons & neck collar on Lv3 Armor
                drawPart(cubeMesh, 0f, 1.44f, -0.02f, 0.26f, 0.09f, 0.24f, 0f, 0f, 0f, 0.20f, 0.22f, 0.25f)
            }
        }

        // 4. VISIBLE TACTICAL BACKPACK (Lv 1 / Lv 2 / Lv 3) + Slung Secondary Weapon
        val packLvl = a.backpackLevel
        val packScale = 0.85f + packLvl * 0.18f
        val (pr, pg, pb) = when (packLvl) {
            0 -> Triple(0.28f, 0.30f, 0.26f) // Compact hydration pouch
            1 -> Triple(0.48f, 0.42f, 0.30f) // Lv1 Tan Daypack
            2 -> Triple(0.26f, 0.34f, 0.22f) // Lv2 Olive Tactical Rucksack
            else -> Triple(0.18f, 0.20f, 0.23f) // Lv3 Heavy Spec-Ops Pack
        }
        drawPart(cubeMesh, 0f, 1.20f, -0.22f, 0.30f * packScale, 0.40f * packScale, 0.16f * packScale, 0f, 0f, 0f, pr, pg, pb)
        if (a.weaponSlots.count { it != null } >= 2) {
            // Slung secondary rifle across the backpack!
            drawPart(limbMesh, 0.04f, 1.24f, -0.30f, 0.07f, 0.82f, 0.08f, 0f, 0f, 36f, 0.16f, 0.17f, 0.19f)
        }

        // 5. Neck, Sculpted Head & VISIBLE PUBG HELMET (Lv0 Cap / Lv1 Moto / Lv2 Military / Lv3 Spetsnaz Visor!)
        drawPart(limbMesh, 0f, 1.48f, 0f, 0.13f, 0.12f, 0.13f, 0f, 0f, 0f, skinR * 0.92f, skinG * 0.92f, skinB * 0.92f)
        drawPart(sphereMesh, 0f, 1.63f, 0.01f, 0.27f, 0.30f, 0.28f, 0f, 0f, 0f, skinR, skinG, skinB)
        // Tactical sunglasses / eyes band
        drawPart(cubeMesh, 0f, 1.65f, 0.13f, 0.19f, 0.055f, 0.04f, 0f, 0f, 0f, 0.08f, 0.09f, 0.11f)

        when (a.helmetLevel) {
            0 -> {
                // Tactical baseball cap / hair crown
                drawPart(sphereMesh, 0f, 1.69f, -0.01f, 0.28f, 0.18f, 0.29f, 0f, 0f, 0f, 0.18f, 0.20f, 0.22f)
                drawPart(cubeMesh, 0f, 1.69f, 0.15f, 0.18f, 0.03f, 0.12f, 10f, 0f, 0f, 0.16f, 0.18f, 0.20f)
            }
            1 -> {
                // Lv1 Motorcycle Helmet (smooth olive-gray dome)
                drawPart(sphereMesh, 0f, 1.67f, -0.01f, 0.33f, 0.28f, 0.34f, 0f, 0f, 0f, 0.34f, 0.39f, 0.34f)
            }
            2 -> {
                // Lv2 Military Ballistic MICH Helmet with rim
                drawPart(sphereMesh, 0f, 1.68f, -0.01f, 0.35f, 0.27f, 0.36f, 0f, 0f, 0f, 0.26f, 0.32f, 0.22f)
                drawPart(cubeMesh, 0f, 1.64f, 0.02f, 0.34f, 0.05f, 0.35f, 0f, 0f, 0f, 0.22f, 0.27f, 0.19f)
            }
            else -> {
                // Lv3 Iconic PUBG Spetsnaz Helmet with full armored faceplate & narrow eye slit!
                drawPart(sphereMesh, 0f, 1.68f, -0.01f, 0.37f, 0.31f, 0.38f, 0f, 0f, 0f, 0.17f, 0.19f, 0.22f)
                drawPart(cubeMesh, 0f, 1.61f, 0.15f, 0.28f, 0.20f, 0.08f, 0f, 0f, 0f, 0.22f, 0.24f, 0.27f) // Welded face visor
                drawPart(cubeMesh, 0f, 1.65f, 0.195f, 0.20f, 0.035f, 0.02f, 0f, 0f, 0f, 0.03f, 0.04f, 0.05f) // Eye slit
            }
        }

        // 6. Articulated Arms (Upper Arm, Forearm, Tactical Gloves) & Detailed Multi-Part Weapon
        val hasGun = a.activeWeapon() != null
        val aimRaise = if (hasGun) (if (a.isAiming || a.shootAnimTimer > 0f) -78f else -52f) else -15f - stride * 0.5f
        // Right shooting arm
        drawPart(limbMesh, 0.28f, 1.22f, 0.06f, 0.13f, 0.32f, 0.13f, aimRaise * 0.65f, 0f, 0f, jacket[0], jacket[1], jacket[2])
        drawPart(limbMesh, 0.24f, 1.15f, 0.26f, 0.11f, 0.30f, 0.11f, aimRaise, 0f, 0f, 0.15f, 0.15f, 0.16f)
        // Left support arm cradling rifle handguard
        val leftAim = if (hasGun) -64f else 15f + stride * 0.5f
        drawPart(limbMesh, -0.26f, 1.22f, 0.08f, 0.13f, 0.32f, 0.13f, leftAim * 0.65f, 0f, 0f, jacket[0], jacket[1], jacket[2])
        drawPart(limbMesh, -0.06f, 1.16f, 0.34f, 0.11f, 0.30f, 0.11f, leftAim, 26f, 0f, 0.15f, 0.15f, 0.16f)

        // Detailed Multi-Part Weapon Model in Hands
        a.activeWeapon()?.let { w ->
            val gunR = 0.16f; val gunG = 0.17f; val gunB = 0.19f
            val accent = w.rarity.rgb
            val barrelLen = when (w.weaponClass) {
                WeaponClass.SNIPER, WeaponClass.MARKSMAN -> 1.12f
                WeaponClass.ROCKET_LAUNCHER -> 1.25f
                WeaponClass.ASSAULT_RIFLE, WeaponClass.LMG, WeaponClass.SHOTGUN -> 0.85f
                else -> 0.48f
            }
            // Receiver & Barrel
            drawPart(cubeMesh, 0.16f, 1.20f, 0.46f, 0.09f, 0.13f, 0.44f, 0f, 0f, 0f, gunR, gunG, gunB)
            drawPart(limbMesh, 0.16f, 1.23f, 0.46f + barrelLen * 0.45f, 0.055f, barrelLen, 0.055f, 90f, 0f, 0f, 0.12f, 0.12f, 0.14f)
            // Magazine & Buttstock
            drawPart(cubeMesh, 0.16f, 1.08f, 0.48f, 0.055f, 0.18f, 0.08f, 14f, 0f, 0f, accent[0] * 0.6f, accent[1] * 0.6f, accent[2] * 0.6f)
            if (barrelLen > 0.6f) {
                drawPart(cubeMesh, 0.16f, 1.18f, 0.18f, 0.07f, 0.12f, 0.24f, 0f, 0f, 0f, 0.22f, 0.19f, 0.16f)
            }
            // Visible Scope or Red Dot on Top Rail
            if (w.hasScope) {
                drawPart(limbMesh, 0.16f, 1.30f, 0.46f, 0.065f, 0.28f, 0.065f, 90f, 0f, 0f, 0.10f, 0.11f, 0.12f)
            }
            // Visible Silencer at Muzzle
            if (w.isSilenced) {
                drawPart(limbMesh, 0.16f, 1.23f, 0.56f + barrelLen * 0.85f, 0.075f, 0.26f, 0.075f, 90f, 0f, 0f, 0.10f, 0.10f, 0.11f)
            }
        }

        // 7. Parachute Canopy & Harness Riser Lines while Dropping
        if (a.dropPhase == DropPhase.PARACHUTE) {
            drawPart(sphereMesh, 0f, 3.6f, 0f, 4.4f, 1.1f, 2.4f, 0f, 0f, 0f, a.parachuteRgb[0], a.parachuteRgb[1], a.parachuteRgb[2])
            drawPart(limbMesh, -0.7f, 2.5f, 0f, 0.04f, 2.1f, 0.04f, 0f, 0f, -22f, 0.2f, 0.2f, 0.2f)
            drawPart(limbMesh, 0.7f, 2.5f, 0f, 0.04f, 2.1f, 0.04f, 0f, 0f, 22f, 0.2f, 0.2f, 0.2f)
        }
    }

    /** Realistic GTA/PUBG SUV / Tactical 4x4 with rounded wheels, cabin glass & headlights */
    private fun drawTacticalVehicle(x: Float, y: Float, z: Float, yaw: Float, rgb: FloatArray) {
        val yd = Math.toDegrees(yaw.toDouble()).toFloat()
        fun part(mesh: GpuMesh, ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float, rotZ: Float, r: Float, g: Float, b: Float) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, x, y, z)
            Matrix.rotateM(modelMat, 0, yd, 0f, 1f, 0f)
            Matrix.translateM(modelMat, 0, ox, oy, oz)
            if (rotZ != 0f) Matrix.rotateM(modelMat, 0, rotZ, 0f, 0f, 1f)
            Matrix.scaleM(modelMat, 0, sx, sy, sz)
            drawLitMesh(mesh, modelMat, r, g, b, 1f)
        }
        // Lower body, hood, cabin roof, tinted windows, grille/headlights, spare tire
        part(cubeMesh, 0f, 0.88f, 0f, 2.25f, 0.78f, 4.55f, 0f, rgb[0], rgb[1], rgb[2])
        part(cubeMesh, 0f, 1.62f, -0.32f, 1.98f, 0.78f, 2.35f, 0f, rgb[0] * 0.9f, rgb[1] * 0.9f, rgb[2] * 0.9f)
        part(cubeMesh, 0f, 1.62f, -0.32f, 2.02f, 0.52f, 2.10f, 0f, 0.14f, 0.19f, 0.24f) // Tinted windows
        part(cubeMesh, 0f, 0.86f, 2.30f, 1.95f, 0.32f, 0.10f, 0f, 0.14f, 0.14f, 0.16f) // Front grille
        part(cubeMesh, -0.76f, 0.92f, 2.32f, 0.34f, 0.20f, 0.08f, 0f, 1.0f, 0.96f, 0.78f) // Headlight L
        part(cubeMesh, 0.76f, 0.92f, 2.32f, 0.34f, 0.20f, 0.08f, 0f, 1.0f, 0.96f, 0.78f) // Headlight R
        part(cubeMesh, -0.76f, 0.92f, -2.30f, 0.34f, 0.18f, 0.08f, 0f, 0.88f, 0.14f, 0.12f) // Taillight L
        part(cubeMesh, 0.76f, 0.92f, -2.30f, 0.34f, 0.18f, 0.08f, 0f, 0.88f, 0.14f, 0.12f) // Taillight R
        // 4 Round Cylinder Tires
        part(limbMesh, -1.14f, 0.46f, 1.40f, 0.86f, 0.42f, 0.86f, 90f, 0.12f, 0.12f, 0.13f)
        part(limbMesh, 1.14f, 0.46f, 1.40f, 0.86f, 0.42f, 0.86f, 90f, 0.12f, 0.12f, 0.13f)
        part(limbMesh, -1.14f, 0.46f, -1.40f, 0.86f, 0.42f, 0.86f, 90f, 0.12f, 0.12f, 0.13f)
        part(limbMesh, 1.14f, 0.46f, -1.40f, 0.86f, 0.42f, 0.86f, 90f, 0.12f, 0.12f, 0.13f)
    }

    private fun drawPlane(x: Float, y: Float, z: Float, yaw: Float) {
        val yd = Math.toDegrees(yaw.toDouble()).toFloat()
        fun box(ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, x, y, z)
            Matrix.rotateM(modelMat, 0, yd, 0f, 1f, 0f)
            Matrix.translateM(modelMat, 0, ox, oy, oz)
            Matrix.scaleM(modelMat, 0, sx, sy, sz)
            drawLitMesh(cubeMesh, modelMat, r, g, b, 1f)
        }
        box(0f, 0f, 0f, 4.4f, 3.8f, 24f, 0.34f, 0.37f, 0.33f) // C-130 Military Olive Transport
        box(0f, 0.8f, 1f, 28f, 0.7f, 4.8f, 0.38f, 0.40f, 0.36f)
        box(0f, 3.2f, -10.5f, 0.8f, 4.8f, 3.8f, 0.28f, 0.31f, 0.27f)
    }

    private fun drawSkyDome(sunDir: FloatArray) {
        val tod = engine.timeOfDay
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glUseProgram(skyProgram)
        System.arraycopy(viewMat, 0, viewProjNoTrans, 0, 16)
        viewProjNoTrans[12] = 0f; viewProjNoTrans[13] = 0f; viewProjNoTrans[14] = 0f
        Matrix.multiplyMM(mvpMat, 0, projMat, 0, viewProjNoTrans, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(skyProgram, "uViewProjNoTrans"), 1, false, mvpMat, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uSkyTop"), 1, tod.skyTop, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uSkyHorizon"), 1, tod.skyHorizon, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uSunDir"), 1, sunDir, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uSunColor"), 1, tod.sunColor, 0)
        GLES30.glBindVertexArray(cubeMesh.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, cubeMesh.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun bindLightingUniforms(program: Int, sunDir: FloatArray, flashlightFlag: Float, shadowFlag: Float, drawDist: Float) {
        val tod = engine.timeOfDay
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uSunDir"), 1, sunDir, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uSunColor"), 1, tod.sunColor, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uAmbient"), tod.ambientStrength)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uFogColor"), 1, tod.fogColor, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFogMaxDist"), drawDist)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uCamPos"), camX, camY, camZ)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uCamFwd"), camFwdX, camFwdY, camFwdZ)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFlashlightOn"), flashlightFlag)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uShadowsEnabled"), shadowFlag)
    }

    private fun drawLitMesh(mesh: GpuMesh, model: FloatArray, r: Float, g: Float, b: Float, useOverride: Float) {
        Matrix.multiplyMM(mvpMat, 0, viewProjMat, 0, model, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(litProgram, "uMVP"), 1, false, mvpMat, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(litProgram, "uModel"), 1, false, model, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(litProgram, "uColorOverride"), r, g, b)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(litProgram, "uUseOverride"), useOverride)
        GLES30.glBindVertexArray(mesh.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun drawFxMesh(mesh: GpuMesh, model: FloatArray, r: Float, g: Float, b: Float, a: Float, mode: Int) {
        Matrix.multiplyMM(mvpMat, 0, viewProjMat, 0, model, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(fxProgram, "uMVP"), 1, false, mvpMat, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(fxProgram, "uModel"), 1, false, model, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(fxProgram, "uColor"), r, g, b, a)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(fxProgram, "uMode"), mode)
        GLES30.glBindVertexArray(mesh.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun drawInstancedProps(mesh: GpuMesh, instVbo: Int, props: List<InstancedProp>, maxDist: Float, density: Float) {
        val maxDistSq = maxDist * maxDist
        val strideStep = if (density < 0.6f) 2 else 1
        var count = 0
        var idx = 0
        while (idx < props.size && count < maxInstances) {
            val p = props[idx]
            idx += strideStep
            if (!isInFrustumAndRange(p.x, p.z, maxDistSq, 8f)) continue
            val base = count * 10
            instanceFloatArray[base] = p.x
            instanceFloatArray[base + 1] = p.y
            instanceFloatArray[base + 2] = p.z
            instanceFloatArray[base + 3] = p.rotY
            instanceFloatArray[base + 4] = p.scaleX
            instanceFloatArray[base + 5] = p.scaleY
            instanceFloatArray[base + 6] = p.scaleZ
            instanceFloatArray[base + 7] = p.r
            instanceFloatArray[base + 8] = p.g
            instanceFloatArray[base + 9] = p.b
            count++
        }
        if (count == 0) return

        instanceBuf.clear()
        instanceBuf.put(instanceFloatArray, 0, count * 10)
        instanceBuf.position(0)

        GLES30.glBindVertexArray(mesh.vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, count * 10 * 4, instanceBuf, GLES30.GL_STREAM_DRAW)

        val stride = 10 * 4
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribDivisor(3, 1)

        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 3, GLES30.GL_FLOAT, false, stride, 4 * 4)
        GLES30.glVertexAttribDivisor(4, 1)

        GLES30.glEnableVertexAttribArray(5)
        GLES30.glVertexAttribPointer(5, 3, GLES30.GL_FLOAT, false, stride, 7 * 4)
        GLES30.glVertexAttribDivisor(5, 1)

        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_INT, 0, count)

        GLES30.glDisableVertexAttribArray(3)
        GLES30.glDisableVertexAttribArray(4)
        GLES30.glDisableVertexAttribArray(5)
        GLES30.glBindVertexArray(0)
    }

    private fun isInFrustumAndRange(x: Float, z: Float, maxDistSq: Float, pad: Float): Boolean {
        val dx = x - camX
        val dz = z - camZ
        val dSq = dx * dx + dz * dz
        if (dSq > maxDistSq) return false
        if (dSq < pad * pad) return true
        val dot = dx * camFwdX + dz * camFwdZ
        return dot > -pad * 1.5f
    }

    private fun projectDamageNumbers(): List<ScreenDamageNumber> {
        val list = ArrayList<ScreenDamageNumber>(12)
        val nums = engine.pools.damageNumbers
        for (i in nums.indices) {
            val d = nums[i]
            if (!d.active) continue
            projVec[0] = d.worldX; projVec[1] = d.worldY; projVec[2] = d.worldZ; projVec[3] = 1f
            Matrix.multiplyMV(projVec, 0, viewProjMat, 0, projVec, 0)
            if (projVec[3] > 0.1f) {
                val ndcX = projVec[0] / projVec[3]
                val ndcY = projVec[1] / projVec[3]
                if (ndcX in -1f..1f && ndcY in -1f..1f) {
                    list.add(
                        ScreenDamageNumber(
                            id = i,
                            normX = (ndcX + 1f) * 0.5f,
                            normY = (1f - ndcY) * 0.5f,
                            amount = d.amount,
                            isHeadshot = d.isHeadshot,
                            isShield = d.isShield,
                            alpha = (d.life / d.maxLife).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }
        return list
    }
}
