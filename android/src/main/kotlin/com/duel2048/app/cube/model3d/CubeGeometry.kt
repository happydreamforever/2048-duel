package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeFace
import com.duel2048.shared.cube.CubeMove
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The cube's logic layout, matching assets/models/rubiks_cube.glb (built by
 * tools/cube-model/build_cube_glb.py): 27 cubies on a unit grid, white U on +Y, yellow D,
 * green F on +Z, blue B, red R on +X, orange L. Owns the exact move<->rotation laws (a WCA
 * face turn is -90° about the face's outward normal, seen from outside), ray picking and
 * drag->move resolution for touch turns. No rendering here - the model asset owns the looks.
 */
class CubeGeometry {

    class Cubie(
        /** Grid cell, each component in -1..1. */
        val grid: IntArray,
        /** Cube-space center at the idle pose ([spacing] per unit). */
        val center: FloatArray,
    ) {
        /** Node name of this cubie in the glb asset. */
        val nodeName: String get() = "cubie_${grid[0]}_${grid[1]}_${grid[2]}"
    }

    /** One turn to animate: a layer's cubies rotate about the world +axis by [degrees]. */
    class TurnSpec(val axis: Int, val coord: Int, val degrees: Float, val members: List<Int>)

    /** A touch hit on a cubie, in cube space (orbit undone, idle pose). */
    class PickHit(val cubie: Int, val point: FloatArray, val normalAxis: Int, val normalSign: Int)

    val cubies: List<Cubie>
    /** Center-to-center distance between neighbouring cubies (world units). */
    val spacing: Float = 1f

    /** WCA face -> (axis 0..2, grid sign -1/+1) of its outer layer; fixed by construction. */
    val faceLayer: Map<CubeFace, Pair<Int, Int>> = FACE_PLACEMENT

    init {
        val built = ArrayList<Cubie>(27)
        for (gz in intArrayOf(-1, 0, 1)) for (gy in intArrayOf(-1, 0, 1)) for (gx in intArrayOf(-1, 0, 1)) {
            built.add(Cubie(intArrayOf(gx, gy, gz), floatArrayOf(gx.toFloat(), gy.toFloat(), gz.toFloat())))
        }
        cubies = built
    }

    /** The cubies of the outer layer of [face]. */
    fun faceCubies(face: CubeFace): List<Int> {
        val (axis, sign) = checkNotNull(faceLayer[face])
        return cubies.indices.filter { cubies[it].grid[axis] == sign }
    }

    /**
     * Exact spec for animating [move]: the face's layer rotates about the world +axis by
     * [degrees] (three-quarter turns animate the short way round, same end state).
     */
    fun turnFor(move: CubeMove): TurnSpec {
        val (axis, sign) = checkNotNull(faceLayer[move.face()])
        val quarterDegrees = -90f * sign
        val degrees = when (move.quarterTurns()) {
            1 -> quarterDegrees
            2 -> 180f
            else -> -quarterDegrees
        }
        return TurnSpec(axis = axis, coord = sign, degrees = degrees, members = faceCubies(move.face()))
    }

    /** Cube move for rotating layer (axis, coord) by [degrees] about +axis; null for middle slices. */
    fun moveForTurn(axis: Int, coord: Int, degrees: Float): CubeMove? {
        if (coord == 0) return null
        for ((face, layer) in faceLayer) {
            if (layer.first == axis && layer.second == coord) {
                val quarterDegrees = -90f * coord
                val quarters = when (degrees) {
                    quarterDegrees -> 1
                    -quarterDegrees -> 3
                    else -> 2
                }
                return moveOf(face, quarters)
            }
        }
        return null
    }

    private fun moveOf(face: CubeFace, quarters: Int): CubeMove = when (face) {
        CubeFace.U -> if (quarters == 1) CubeMove.U else if (quarters == 2) CubeMove.U2 else CubeMove.Ui
        CubeFace.R -> if (quarters == 1) CubeMove.R else if (quarters == 2) CubeMove.R2 else CubeMove.Ri
        CubeFace.F -> if (quarters == 1) CubeMove.F else if (quarters == 2) CubeMove.F2 else CubeMove.Fi
        CubeFace.D -> if (quarters == 1) CubeMove.D else if (quarters == 2) CubeMove.D2 else CubeMove.Di
        CubeFace.L -> if (quarters == 1) CubeMove.L else if (quarters == 2) CubeMove.L2 else CubeMove.Li
        CubeFace.B -> if (quarters == 1) CubeMove.B else if (quarters == 2) CubeMove.B2 else CubeMove.Bi
    }

    // ---- touch picking -----------------------------------------------------------------------

    /**
     * Nearest cubie hit by a cube-space ray (origin o, direction d). [poseOf] supplies each
     * cubie's current accumulated layer-turn matrix (a pure rotation about the origin);
     * gestures only act when idle, so the hit normal is axis-aligned in cube space either way.
     */
    fun pick(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, poseOf: (Int) -> FloatArray = { CubeMath.identity() }): PickHit? {
        var bestT = Float.MAX_VALUE
        var best: PickHit? = null
        for ((cubieIndex, cubie) in cubies.withIndex()) {
            val inv = CubeMath.inverse(poseOf(cubieIndex)) ?: continue
            // Ray in the cubie's rotated frame, where its box stays axis-aligned.
            val lo = CubeMath.transformPoint(inv, ox, oy, oz)
            val ld = CubeMath.transformDir(inv, dx, dy, dz)
            val lc = CubeMath.transformPoint(inv, cubie.center[0], cubie.center[1], cubie.center[2])
            val min = floatArrayOf(lc[0] - BODY_HALF, lc[1] - BODY_HALF, lc[2] - BODY_HALF)
            val max = floatArrayOf(lc[0] + BODY_HALF, lc[1] + BODY_HALF, lc[2] + BODY_HALF)
            val t = CubeMath.rayAabb(lo[0], lo[1], lo[2], ld[0], ld[1], ld[2], min, max) ?: continue
            if (t >= bestT) continue
            bestT = t
            // Struck face: the axis where the hit point sits closest to the box surface.
            val hit = floatArrayOf(lo[0] + ld[0] * t, lo[1] + ld[1] * t, lo[2] + ld[2] * t)
            var axis = 0
            var bestAxisDist = Float.MAX_VALUE
            for (a in 0 until 3) {
                val dist = BODY_HALF - abs(hit[a] - lc[a])
                if (dist < bestAxisDist) { bestAxisDist = dist; axis = a }
            }
            val localSign = if (hit[axis] >= lc[axis]) 1 else -1
            val nLocal = FloatArray(3)
            nLocal[axis] = localSign.toFloat()
            // Back to cube space (rotations keep it axis-aligned; snap numerics).
            val nCube = CubeMath.transformDir(poseOf(cubieIndex), nLocal[0], nLocal[1], nLocal[2])
            var cubeAxis = 0
            var maxComp = 0f
            var cubeSign = 1
            for (a in 0 until 3) if (abs(nCube[a]) > maxComp) { maxComp = abs(nCube[a]); cubeAxis = a; cubeSign = if (nCube[a] >= 0f) 1 else -1 }
            best = PickHit(cubieIndex, floatArrayOf(ox + dx * t, oy + dy * t, oz + dz * t), cubeAxis, cubeSign)
        }
        return best
    }

    /**
     * Resolves a drag that started on a picked face into a [CubeMove]: the drag picks one of
     * the face's two tangent axes; rotation axis = normal x tangent; the layer comes from the
     * hit point; the sign from the direction the layer would move under the drag.
     *
     * @param dragX/dragY drag vector in pixels
     * @param project     cube-space point -> screen pixels (x, y), using the current camera
     */
    fun resolveDragMove(hit: PickHit, dragX: Float, dragY: Float, project: (FloatArray) -> FloatArray): CubeMove? {
        val dragLen = sqrt(dragX * dragX + dragY * dragY)
        if (dragLen < 1e-3f) return null
        val drag = floatArrayOf(dragX / dragLen, dragY / dragLen)

        val tangentAxes = (0 until 3).filter { it != hit.normalAxis }
        var bestAxis = -1
        var bestSign = 1
        var bestScore = 0f
        for (axis in tangentAxes) {
            val t0 = project(hit.point)
            val p2 = hit.point.copyOf()
            p2[axis] += spacing * 0.35f
            val t1 = project(p2)
            val ex = t1[0] - t0[0]
            val ey = t1[1] - t0[1]
            val elen = sqrt(ex * ex + ey * ey)
            if (elen < 1e-3f) continue
            val score = (ex / elen) * drag[0] + (ey / elen) * drag[1]
            if (abs(score) > abs(bestScore)) { bestScore = score; bestAxis = axis; bestSign = if (score >= 0f) 1 else -1 }
        }
        if (bestAxis < 0) return null

        val n = FloatArray(3)
        n[hit.normalAxis] = hit.normalSign.toFloat()
        val t = FloatArray(3)
        t[bestAxis] = bestSign.toFloat()
        val rot = CubeMath.cross(n, t)
        var rotAxis = 0
        var rotSign = 1f
        var maxComp = 0f
        for (a in 0 until 3) {
            if (abs(rot[a]) > maxComp) { maxComp = abs(rot[a]); rotAxis = a; rotSign = if (rot[a] >= 0f) 1f else -1f }
        }
        if (maxComp < 0.5f) return null // drag along the face normal — no turn

        // Dragging along t moves the hit point with velocity axisPositive x p; aligning that
        // with the drag fixes the rotation direction.
        val axisVec = FloatArray(3)
        axisVec[rotAxis] = 1f
        val v = CubeMath.cross(axisVec, hit.point)
        val degreesAboutPositiveAxis = if (CubeMath.dot(v, t) >= 0f) 90f * rotSign else -90f * rotSign

        val layerCoord = (hit.point[rotAxis] / spacing).roundToInt()
        return moveForTurn(rotAxis, layerCoord, degreesAboutPositiveAxis)
    }

    companion object {
        /** Axis (0=x, 1=y, 2=z) and grid sign of each WCA face's outer layer. */
        val FACE_PLACEMENT: Map<CubeFace, Pair<Int, Int>> = mapOf(
            CubeFace.U to Pair(1, 1),
            CubeFace.D to Pair(1, -1),
            CubeFace.F to Pair(2, 1),
            CubeFace.B to Pair(2, -1),
            CubeFace.R to Pair(0, 1),
            CubeFace.L to Pair(0, -1),
        )

        /** The model's cubies fill their whole cell (tiles sit on the outer faces). */
        private const val BODY_HALF = 0.5f
    }
}
