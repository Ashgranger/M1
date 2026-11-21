package com.example.game.render

import android.opengl.GLES30
import com.example.game.config.WorldConfig
import com.example.game.world.RoadNetwork
import com.example.game.world.TerrainGenerator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// ============================================================================
// SECTION: MESH FACTORY — Terrain, Roads, Realistic Soldier Primitives & Props
// ============================================================================

data class GpuMesh(
    val vao: Int,
    val vbo: Int,
    val ibo: Int,
    val indexCount: Int
)

object MeshFactory {

    // Interleaved layout: pos(3) + normal(3) + color(3) = 9 floats = 36 bytes
    private fun uploadInterleaved(vertices: FloatArray, indices: IntArray): GpuMesh {
        val vaos = IntArray(1); GLES30.glGenVertexArrays(1, vaos, 0)
        val vbos = IntArray(1); GLES30.glGenBuffers(1, vbos, 0)
        val ibos = IntArray(1); GLES30.glGenBuffers(1, ibos, 0)

        GLES30.glBindVertexArray(vaos[0])
        val vb: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(vertices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)

        val ib: IntBuffer = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        ib.put(indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibos[0])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, ib, GLES30.GL_STATIC_DRAW)

        val stride = 9 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glBindVertexArray(0)

        return GpuMesh(vaos[0], vbos[0], ibos[0], indices.size)
    }

    fun createTerrainMesh(terrain: TerrainGenerator, gridRes: Int = 112): GpuMesh {
        val vertsPerSide = gridRes + 1
        val verts = FloatArray(vertsPerSide * vertsPerSide * 9)
        val normal = FloatArray(3)
        val color = FloatArray(3)
        val step = WorldConfig.WORLD_SIZE / gridRes

        var vi = 0
        for (gz in 0..gridRes) {
            val z = -WorldConfig.HALF_WORLD + gz * step
            for (gx in 0..gridRes) {
                val x = -WorldConfig.HALF_WORLD + gx * step
                val y = terrain.getHeight(x, z)
                terrain.getNormal(x, z, normal)
                terrain.getColor(x, z, y, normal[1], color)
                verts[vi++] = x; verts[vi++] = y; verts[vi++] = z
                verts[vi++] = normal[0]; verts[vi++] = normal[1]; verts[vi++] = normal[2]
                verts[vi++] = color[0]; verts[vi++] = color[1]; verts[vi++] = color[2]
            }
        }

        val indices = IntArray(gridRes * gridRes * 6)
        var ii = 0
        for (gz in 0 until gridRes) {
            for (gx in 0 until gridRes) {
                val tl = gz * vertsPerSide + gx
                val tr = tl + 1
                val bl = tl + vertsPerSide
                val br = bl + 1
                indices[ii++] = tl; indices[ii++] = bl; indices[ii++] = tr
                indices[ii++] = tr; indices[ii++] = bl; indices[ii++] = br
            }
        }
        return uploadInterleaved(verts, indices)
    }

    /** Builds a single GPU mesh for all paved asphalt roads, curbs & yellow dashed lane markings! */
    fun createRoadNetworkMesh(terrain: TerrainGenerator, roadNetwork: RoadNetwork): GpuMesh {
        val verts = ArrayList<Float>(12000)
        val idx = ArrayList<Int>(4000)

        fun addQuad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            r: Float, g: Float, b: Float
        ) {
            val base = verts.size / 9
            verts.addAll(listOf(x0, y0, z0, 0f, 1f, 0f, r, g, b))
            verts.addAll(listOf(x1, y1, z1, 0f, 1f, 0f, r, g, b))
            verts.addAll(listOf(x2, y2, z2, 0f, 1f, 0f, r, g, b))
            verts.addAll(listOf(x3, y3, z3, 0f, 1f, 0f, r, g, b))
            idx.add(base); idx.add(base + 2); idx.add(base + 1)
            idx.add(base); idx.add(base + 3); idx.add(base + 2)
        }

        for (seg in roadNetwork.segments) {
            val dx = seg.x2 - seg.x1
            val dz = seg.z2 - seg.z1
            val len = hypot(dx, dz).coerceAtLeast(1f)
            val ux = dx / len
            val uz = dz / len
            val rx = -uz
            val rz = ux
            val halfW = seg.width * 0.5f
            val slices = (len / 14f).toInt().coerceIn(2, 40)

            val ar = if (seg.isDirt) 0.45f else 0.17f
            val ag = if (seg.isDirt) 0.37f else 0.18f
            val ab = if (seg.isDirt) 0.26f else 0.20f

            for (s in 0 until slices) {
                val t0 = s.toFloat() / slices
                val t1 = (s + 1).toFloat() / slices
                val ax = seg.x1 + dx * t0
                val az = seg.z1 + dz * t0
                val bx = seg.x1 + dx * t1
                val bz = seg.z1 + dz * t1

                val hA = max(terrain.getHeight(ax, az), 4.15f) + 0.16f
                val hB = max(terrain.getHeight(bx, bz), 4.15f) + 0.16f

                // Paved asphalt ribbon
                addQuad(
                    ax - rx * halfW, hA, az - rz * halfW,
                    ax + rx * halfW, hA, az + rz * halfW,
                    bx + rx * halfW, hB, bz + rz * halfW,
                    bx - rx * halfW, hB, bz - rz * halfW,
                    ar, ag, ab
                )

                // Yellow dashed center lane marking on paved roads
                if (!seg.isDirt && s % 2 == 0) {
                    val lw = 0.28f
                    addQuad(
                        ax - rx * lw, hA + 0.05f, az - rz * lw,
                        ax + rx * lw, hA + 0.05f, az + rz * lw,
                        bx + rx * lw, hB + 0.05f, bz + rz * lw,
                        bx - rx * lw, hB + 0.05f, bz - rz * lw,
                        0.88f, 0.74f, 0.18f
                    )
                }
            }
        }
        return uploadInterleaved(verts.toFloatArray(), idx.toIntArray())
    }

    fun createUnitCube(): GpuMesh {
        val verts = ArrayList<Float>(24 * 9)
        val idx = ArrayList<Int>(36)
        fun addFace(nx: Float, ny: Float, nz: Float, p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray) {
            val base = verts.size / 9
            for (p in listOf(p0, p1, p2, p3)) {
                verts.add(p[0]); verts.add(p[1]); verts.add(p[2])
                verts.add(nx); verts.add(ny); verts.add(nz)
                verts.add(1f); verts.add(1f); verts.add(1f)
            }
            idx.add(base); idx.add(base + 1); idx.add(base + 2)
            idx.add(base); idx.add(base + 2); idx.add(base + 3)
        }
        val h = 0.5f
        addFace(0f, 0f, 1f, floatArrayOf(-h, -h, h), floatArrayOf(h, -h, h), floatArrayOf(h, h, h), floatArrayOf(-h, h, h))
        addFace(0f, 0f, -1f, floatArrayOf(h, -h, -h), floatArrayOf(-h, -h, -h), floatArrayOf(-h, h, -h), floatArrayOf(h, h, -h))
        addFace(1f, 0f, 0f, floatArrayOf(h, -h, h), floatArrayOf(h, -h, -h), floatArrayOf(h, h, -h), floatArrayOf(h, h, h))
        addFace(-1f, 0f, 0f, floatArrayOf(-h, -h, -h), floatArrayOf(-h, -h, h), floatArrayOf(-h, h, h), floatArrayOf(-h, h, -h))
        addFace(0f, 1f, 0f, floatArrayOf(-h, h, h), floatArrayOf(h, h, h), floatArrayOf(h, h, -h), floatArrayOf(-h, h, -h))
        addFace(0f, -1f, 0f, floatArrayOf(-h, -h, -h), floatArrayOf(h, -h, -h), floatArrayOf(h, -h, h), floatArrayOf(-h, -h, h))
        return uploadInterleaved(verts.toFloatArray(), idx.toIntArray())
    }

    /** Smooth low-poly sphere for realistic soldier heads, shoulders, helmets & joint caps. */
    fun createSphereMesh(stacks: Int = 8, slices: Int = 12): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Int>()
        for (st in 0..stacks) {
            val phi = (st.toFloat() / stacks) * PI.toFloat()
            val y = cos(phi) * 0.5f
            val r = sin(phi) * 0.5f
            for (sl in 0..slices) {
                val theta = (sl.toFloat() / slices) * 2f * PI.toFloat()
                val x = cos(theta) * r
                val z = sin(theta) * r
                val nx = cos(theta) * sin(phi)
                val ny = cos(phi)
                val nz = sin(theta) * sin(phi)
                verts.addAll(listOf(x, y, z, nx, ny, nz, 1f, 1f, 1f))
            }
        }
        val ring = slices + 1
        for (st in 0 until stacks) {
            for (sl in 0 until slices) {
                val a = st * ring + sl
                val b = a + 1
                val c = a + ring
                val d = c + 1
                idx.add(a); idx.add(c); idx.add(b)
                idx.add(b); idx.add(c); idx.add(d)
            }
        }
        return uploadInterleaved(verts.toFloatArray(), idx.toIntArray())
    }

    /** Closed tapered cylinder for anatomical limbs, rifle barrels, scopes & vehicle wheels. */
    fun createLimbCylinderMesh(sides: Int = 10, topScale: Float = 0.92f): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Int>()
        for (i in 0..sides) {
            val a = (i.toFloat() / sides) * 2f * PI.toFloat()
            val cx = cos(a); val cz = sin(a)
            verts.addAll(listOf(cx * 0.5f, -0.5f, cz * 0.5f, cx, 0f, cz, 1f, 1f, 1f))
            verts.addAll(listOf(cx * 0.5f * topScale, 0.5f, cz * 0.5f * topScale, cx, 0f, cz, 1f, 1f, 1f))
        }
        for (i in 0 until sides) {
            val b = i * 2
            idx.add(b); idx.add(b + 2); idx.add(b + 1)
            idx.add(b + 1); idx.add(b + 2); idx.add(b + 3)
        }
        // Caps
        val botCenter = verts.size / 9
        verts.addAll(listOf(0f, -0.5f, 0f, 0f, -1f, 0f, 1f, 1f, 1f))
        val topCenter = verts.size / 9
        verts.addAll(listOf(0f, 0.5f, 0f, 0f, 1f, 0f, 1f, 1f, 1f))
        for (i in 0 until sides) {
            val b = i * 2
            idx.add(botCenter); idx.add(b); idx.add(b + 2)
            idx.add(topCenter); idx.add(b + 3); idx.add(b + 1)
        }
        return uploadInterleaved(verts.toFloatArray(), idx.toIntArray())
    }

    fun createConeMesh(sides: Int = 7): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Int>()
        for (i in 0 until sides) {
            val a0 = (i.toFloat() / sides) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / sides) * 2f * PI.toFloat()
            val am = (a0 + a1) * 0.5f
            val nx = cos(am); val nz = sin(am)
            val base = verts.size / 9
            verts.addAll(listOf(0f, 0.5f, 0f, nx * 0.8f, 0.6f, nz * 0.8f, 1f, 1f, 1f))
            verts.addAll(listOf(cos(a0) * 0.5f, -0.5f, sin(a0) * 0.5f, nx * 0.8f, 0.4f, nz * 0.8f, 1f, 1f, 1f))
            verts.addAll(listOf(cos(a1) * 0.5f, -0.5f, sin(a1) * 0.5f, nx * 0.8f, 0.4f, nz * 0.8f, 1f, 1f, 1f))
            idx.add(base); idx.add(base + 2); idx.add(base + 1)
        }
        return uploadInterleaved(verts.toFloatArray(), idx.toIntArray())
    }

    fun createOpenCylinder(sides: Int = 48): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Int>()
        for (i in 0..sides) {
            val a = (i.toFloat() / sides) * 2f * PI.toFloat()
            val x = cos(a); val z = sin(a)
            verts.addAll(listOf(x, 0f, z, x, 0f, z, 1f, 1f, 1f))
            verts.addAll(listOf(x, 1f, z, x, 0f, z, 1f, 1f, 1f))
        }
        for (i in 0 until sides) {
            val b = i * 2
            idx.add(b); idx.add(b + 1); idx.add(b + 2)
            idx.add(b + 2); idx.add(b + 1); idx.add(b + 3)
        }
        return uploadInterleaved(verts.toFloatArray(), idx.toIntArray())
    }

    fun createWaterPlane(): GpuMesh {
        val s = 1800f
        val verts = floatArrayOf(
            -s, 0f, -s, 0f, 1f, 0f, 1f, 1f, 1f,
            s, 0f, -s, 0f, 1f, 0f, 1f, 1f, 1f,
            s, 0f, s, 0f, 1f, 0f, 1f, 1f, 1f,
            -s, 0f, s, 0f, 1f, 0f, 1f, 1f, 1f
        )
        val idx = intArrayOf(0, 2, 1, 0, 3, 2)
        return uploadInterleaved(verts, idx)
    }
}
