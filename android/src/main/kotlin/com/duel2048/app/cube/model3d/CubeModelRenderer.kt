package com.duel2048.app.cube.model3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.duel2048.shared.cube.CubeMove
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLES2 renderer for the hand-built cube: per-cubie mesh parts (plastic + stickers) with a
 * two-light + specular shader. Layer turns are animated by rotating the 9 member cubies about
 * a world axis and then committing the exact quarter-turn matrix (drift-free). Touch picking
 * reuses the same matrices, so gestures and rendering always agree.
 */
class CubeModelRenderer(val geometry: CubeGeometry) : GLSurfaceView.Renderer {

    private val lock = Any()

    // Accumulated pose per cubie in cube space (products of exact quarter-turn matrices).
    private var base: Array<FloatArray> = Array(geometry.cubies.size) { CubeMath.identity() }

    private class PendingMove(val axis: Int, val coord: Int, val degrees: Float, val members: List<Int>, val durationMs: Long) {
        var startMs: Long = 0
    }

    private val queue = ArrayDeque<PendingMove>()
    private var active: PendingMove? = null
    private var activeProgress = 0f // eased 0..1

    private var azimuth = 32f
    private var elevation = 24f

    private var surfaceW = 1
    private var surfaceH = 1
    private var cameraDistance = 3f
    private var fovY = 34f

    /** Uploaded GPU copy of one mesh part. */
    private class GpuPart(val vbo: Int, val ibo: Int, val count: Int, val color: FloatArray, val gloss: Float)

    // GL objects (rebuilt on every surface create).
    private var program = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uGloss = 0
    private var aPosition = 0
    private var aNormal = 0
    /** Per cubie: its parts in upload order. */
    private var gpuCubies: List<List<GpuPart>> = emptyList()

    // Scratch to avoid per-frame allocation.
    private val mProjection = FloatArray(16)
    private val mView = FloatArray(16)
    private val mPv = FloatArray(16)
    private val mOrbit = FloatArray(16)
    private val mRot = FloatArray(16)
    private val mNode = FloatArray(16)
    private val mMvp = FloatArray(16)
    private val mTmpA = FloatArray(16)

    fun applyScrambleAnimated(notations: List<String>) {
        val moves = notations.mapNotNull { CubeMove.parse(it) }
        synchronized(lock) {
            for (i in base.indices) System.arraycopy(CubeMath.identity(), 0, base[i], 0, 16)
            queue.clear()
            active = null
            for (move in moves) queue.add(pendingFor(move, SCRAMBLE_MS))
        }
    }

    fun applyMoveAnimated(move: CubeMove) {
        synchronized(lock) { queue.add(pendingFor(move, MOVE_MS)) }
    }

    fun isBusy(): Boolean = synchronized(lock) { queue.isNotEmpty() || active != null }

    fun reset() = synchronized(lock) {
        queue.clear()
        active = null
        for (i in base.indices) System.arraycopy(CubeMath.identity(), 0, base[i], 0, 16)
    }

    private fun pendingFor(move: CubeMove, ms: Long): PendingMove {
        val spec = geometry.turnFor(move)
        return PendingMove(spec.axis, spec.coord, spec.degrees, spec.members, if (move.quarterTurns() == 2) ms * 3 / 2 else ms)
    }

    fun orbitBy(dAzimuth: Float, dElevation: Float) {
        synchronized(lock) {
            azimuth += dAzimuth
            elevation = (elevation + dElevation).coerceIn(-80f, 80f)
        }
    }

    /** Cube-space ray through a view pixel (x, y) for picking; null before the first frame. */
    fun pickRay(x: Float, y: Float): Pair<FloatArray, FloatArray>? {
        val inv = synchronized(lock) {
            if (surfaceW <= 1 || surfaceH <= 1) return null
            updateOrbitMatrix()
            val pvOrbit = CubeMath.multiply(currentPv(), mOrbit)
            CubeMath.inverse(pvOrbit) ?: return null
        }
        val ndcX = 2f * x / surfaceW - 1f
        val ndcY = 1f - 2f * y / surfaceH
        val p0 = CubeMath.transformPoint(inv, ndcX, ndcY, -1f)
        val p1 = CubeMath.transformPoint(inv, ndcX, ndcY, 1f)
        val dir = CubeMath.normalize(floatArrayOf(p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]))
        return Pair(p0, dir)
    }

    /** Cube-space point -> screen pixels (uses the current camera state). */
    fun projectToScreen(p: FloatArray): FloatArray? {
        val pvOrbit = synchronized(lock) {
            if (surfaceW <= 1 || surfaceH <= 1) return null
            updateOrbitMatrix()
            CubeMath.multiply(currentPv(), mOrbit)
        }
        val ndc = CubeMath.transformPoint(pvOrbit, p[0], p[1], p[2])
        return floatArrayOf((ndc[0] + 1f) * 0.5f * surfaceW, (1f - ndc[1]) * 0.5f * surfaceH)
    }

    fun pick(x: Float, y: Float): CubeGeometry.PickHit? {
        val (origin, dir) = pickRay(x, y) ?: return null
        // Snapshot the poses so a move queued mid-pick can't tear a matrix read.
        val poses = synchronized(lock) { if (isBusyLocked()) null else base.map { it.copyOf() } } ?: return null
        return geometry.pick(origin[0], origin[1], origin[2], dir[0], dir[1], dir[2]) { i -> poses[i] }
    }

    private fun isBusyLocked(): Boolean = queue.isNotEmpty() || active != null

    // ---- GLSurfaceView.Renderer --------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram()
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uGloss = GLES20.glGetUniformLocation(program, "uGloss")
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 0f)

        // Upload one interleaved position+normal VBO per mesh part.
        gpuCubies = geometry.cubies.map { cubie ->
            cubie.parts.map { part ->
                val count = part.positions.size / 3
                val interleaved = FloatArray(count * 6)
                for (v in 0 until count) {
                    interleaved[v * 6] = part.positions[v * 3]
                    interleaved[v * 6 + 1] = part.positions[v * 3 + 1]
                    interleaved[v * 6 + 2] = part.positions[v * 3 + 2]
                    interleaved[v * 6 + 3] = part.normals[v * 3]
                    interleaved[v * 6 + 4] = part.normals[v * 3 + 1]
                    interleaved[v * 6 + 5] = part.normals[v * 3 + 2]
                }
                val vbo = IntArray(1)
                val ibo = IntArray(1)
                GLES20.glGenBuffers(1, vbo, 0)
                GLES20.glGenBuffers(1, ibo, 0)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, interleaved.size * 4, floatBuffer(interleaved), GLES20.GL_STATIC_DRAW)
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
                GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, part.indices.size * 2, shortBuffer(part.indices), GLES20.GL_STATIC_DRAW)
                GpuPart(vbo[0], ibo[0], part.indices.size, part.color, part.gloss)
            }
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        // Frame the whole cube: distance from its outer radius and the vertical fov.
        val radius = 1.74f * geometry.spacing
        cameraDistance = radius / kotlin.math.tan(Math.toRadians((fovY / 2f).toDouble())).toFloat() * 1.12f
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        synchronized(lock) {
            surfaceW = width
            surfaceH = height
            val aspect = if (height > 0) width.toFloat() / height else 1f
            System.arraycopy(CubeMath.perspective(fovY, aspect, 0.05f, 40f), 0, mProjection, 0, 16)
            System.arraycopy(CubeMath.translation(0f, 0f, -cameraDistance), 0, mView, 0, 16)
            CubeMath.multiply(mProjection, mView, mPv)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        synchronized(lock) {
            advanceAnimationLocked()
            updateOrbitMatrix()

            for ((cubieIndex, cubieParts) in gpuCubies.withIndex()) {
                System.arraycopy(base[cubieIndex], 0, mNode, 0, 16)
                val anim = active
                if (anim != null && anim.members.contains(cubieIndex)) {
                    val axis = axisUnit(anim.axis)
                    System.arraycopy(CubeMath.rotationAxis(axis[0], axis[1], axis[2], anim.degrees * activeProgress), 0, mRot, 0, 16)
                    CubeMath.multiply(mRot, mNode, mTmpA)
                    System.arraycopy(mTmpA, 0, mNode, 0, 16)
                }
                CubeMath.multiply(mOrbit, mNode, mTmpA)
                CubeMath.multiply(mPv, mTmpA, mMvp)
                System.arraycopy(mTmpA, 0, mNode, 0, 16)

                GLES20.glUniformMatrix4fv(uMvp, 1, false, mMvp, 0)
                GLES20.glUniformMatrix4fv(uModel, 1, false, mNode, 0)
                for (part in cubieParts) {
                    GLES20.glUniform4f(uColor, part.color[0], part.color[1], part.color[2], part.color[3])
                    GLES20.glUniform1f(uGloss, part.gloss)
                    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, part.vbo)
                    GLES20.glEnableVertexAttribArray(aPosition)
                    GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 24, 0)
                    GLES20.glEnableVertexAttribArray(aNormal)
                    GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, 12)
                    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, part.ibo)
                    GLES20.glDrawElements(GLES20.GL_TRIANGLES, part.count, GLES20.GL_UNSIGNED_SHORT, 0)
                }
            }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        }
    }

    /** Starts the next queued turn and eases the active one; commits finished turns exactly. */
    private fun advanceAnimationLocked() {
        val now = SystemClock.elapsedRealtime()
        var anim = active
        if (anim == null) {
            anim = queue.removeFirstOrNull() ?: return
            anim.startMs = now
            active = anim
        }
        val t = (now - anim.startMs).coerceAtLeast(0).toFloat() / anim.durationMs
        if (t >= 1f) {
            // Commit the exact quarter turn so float error never accumulates.
            val axis = axisUnit(anim.axis)
            val exact = CubeMath.rotationAxis(axis[0], axis[1], axis[2], anim.degrees)
            for (member in anim.members) {
                val next = FloatArray(16)
                CubeMath.multiply(exact, base[member], next)
                base[member] = next
            }
            active = null
            activeProgress = 0f
        } else {
            activeProgress = t * t * (3f - 2f * t) // smoothstep
        }
    }

    private fun updateOrbitMatrix() {
        // NOTE: callers hold [lock].
        val rx = CubeMath.rotationAxis(1f, 0f, 0f, elevation)
        val ry = CubeMath.rotationAxis(0f, 1f, 0f, azimuth)
        CubeMath.multiply(rx, ry, mOrbit)
    }

    private fun currentPv(): FloatArray = mPv

    private fun axisUnit(axis: Int): FloatArray = when (axis) {
        0 -> floatArrayOf(1f, 0f, 0f)
        1 -> floatArrayOf(0f, 1f, 0f)
        else -> floatArrayOf(0f, 0f, 1f)
    }

    // ---- shader plumbing ---------------------------------------------------------------------

    private fun buildProgram(): Int {
        val vertex = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz) * aNormal;
            }
        """.trimIndent()
        // Normals land in view space (the orbit is part of uModel and the camera looks down -Z),
        // so the lighting stays fixed while the cube orbits.
        val fragment = """
            precision mediump float;
            varying vec3 vNormal;
            uniform vec4 uColor;
            uniform float uGloss;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 keyDir = normalize(vec3(0.40, 0.85, 0.50));
                vec3 fillDir = normalize(vec3(-0.60, -0.20, 0.55));
                float key = max(dot(n, keyDir), 0.0);
                float fill = max(dot(n, fillDir), 0.0);
                float b = 0.30 + 0.70 * key + 0.16 * fill;
                float spec = pow(max(dot(reflect(-keyDir, n), vec3(0.0, 0.0, 1.0)), 0.0), 30.0);
                vec3 c = uColor.rgb * b + uGloss * spec * vec3(1.0);
                gl_FragColor = vec4(c, uColor.a);
            }
        """.trimIndent()
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] == GLES20.GL_TRUE) { "cube shader link failed: " + GLES20.glGetProgramInfoLog(p) }
        return p
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] == GLES20.GL_TRUE) { "cube shader compile failed: " + GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values); position(0)
        }

    private fun shortBuffer(values: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(values); position(0)
        }

    companion object {
        private const val SCRAMBLE_MS = 110L
        private const val MOVE_MS = 200L
    }
}
