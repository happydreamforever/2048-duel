package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeMove

/**
 * Layer-turn animation state for one cube. Each cubie has an accumulated pose (a product of
 * exact quarter-turn rotations about the origin); the active turn rotates its 9 members by an
 * eased angle and, when done, commits the exact quarter-turn matrix so float error never
 * accumulates. Pure Kotlin: the view asks for [frame] each tick and pushes the result into
 * the renderer's node transforms.
 */
class CubeAnimator(val geometry: CubeGeometry, private val clock: () -> Long) {

    private class PendingMove(val axis: Int, val degrees: Float, val members: List<Int>, val durationMs: Long) {
        var startMs = 0L
    }

    private val base: Array<FloatArray> = Array(geometry.cubies.size) { CubeMath.identity() }
    private val centers: List<FloatArray> = geometry.cubies.map { CubeMath.translation(it.center[0], it.center[1], it.center[2]) }
    private val queue = ArrayDeque<PendingMove>()
    private var active: PendingMove? = null

    /** Reset to solved and play the whole scramble quickly. */
    fun applyScramble(notations: List<String>) {
        reset()
        for (move in notations.mapNotNull { CubeMove.parse(it) }) queue.add(pendingFor(move, SCRAMBLE_MS))
    }

    fun applyMove(move: CubeMove) {
        queue.add(pendingFor(move, MOVE_MS))
    }

    fun isBusy(): Boolean = queue.isNotEmpty() || active != null

    fun reset() {
        queue.clear()
        active = null
        for (pose in base) System.arraycopy(IDENTITY, 0, pose, 0, 16)
    }

    /**
     * Advances the animation to now and returns every cubie's local transform in the glb's
     * node space: pose · translate(grid center). Returns whether anything is still moving.
     */
    fun frame(out: Array<FloatArray>): Boolean {
        val anim = advance()
        for (i in geometry.cubies.indices) {
            var pose = base[i]
            if (anim != null && anim.first.members.contains(i)) {
                val a = axisUnit(anim.first.axis)
                pose = CubeMath.multiply(CubeMath.rotationAxis(a[0], a[1], a[2], anim.first.degrees * anim.second), base[i])
            }
            CubeMath.multiply(pose, centers[i], out[i])
        }
        return isBusy()
    }

    /** Nearest cubie face under a cube-space ray; only while idle (poses are exact then). */
    fun pick(origin: FloatArray, dir: FloatArray): CubeGeometry.PickHit? {
        if (isBusy()) return null
        return geometry.pick(origin[0], origin[1], origin[2], dir[0], dir[1], dir[2]) { i -> base[i] }
    }

    /** Pose of a cubie (exact while idle); exposed for tests. */
    fun poseOf(cubie: Int): FloatArray = base[cubie].copyOf()

    /** Starts the next queued turn, commits finished ones; -> (active turn, eased progress). */
    private fun advance(): Pair<PendingMove, Float>? {
        val now = clock()
        while (true) {
            val anim = active ?: (queue.removeFirstOrNull()?.also { it.startMs = now; active = it } ?: return null)
            val t = (now - anim.startMs).coerceAtLeast(0).toFloat() / anim.durationMs
            if (t < 1f) return Pair(anim, t * t * (3f - 2f * t)) // smoothstep
            // Commit the exact quarter turn so float error never accumulates.
            val a = axisUnit(anim.axis)
            val exact = CubeMath.rotationAxis(a[0], a[1], a[2], anim.degrees)
            for (member in anim.members) base[member] = CubeMath.multiply(exact, base[member])
            active = null
            // A turn that finished long ago must not delay the next one: chain from its end.
            val overshoot = now - (anim.startMs + anim.durationMs)
            queue.firstOrNull()?.let { next -> next.startMs = now - overshoot; active = queue.removeFirst() }
                ?: return null
        }
    }

    private fun pendingFor(move: CubeMove, ms: Long): PendingMove {
        val spec = geometry.turnFor(move)
        return PendingMove(spec.axis, spec.degrees, spec.members, if (move.quarterTurns() == 2) ms * 3 / 2 else ms)
    }

    private fun axisUnit(axis: Int): FloatArray = when (axis) {
        0 -> floatArrayOf(1f, 0f, 0f)
        1 -> floatArrayOf(0f, 1f, 0f)
        else -> floatArrayOf(0f, 0f, 1f)
    }

    companion object {
        const val SCRAMBLE_MS = 110L
        const val MOVE_MS = 200L
        private val IDENTITY = CubeMath.identity()
    }
}
