package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeFace
import com.duel2048.shared.cube.CubeMove
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The hand-built cube: 27 chamfered cubies with rounded inset stickers, WCA colors baked in
 * (white U on +Y, yellow D, green F on +Z, blue B, red R on +X, orange L) so no face or color
 * is ever inferred from external data. Also owns the exact move<->rotation laws (a WCA face
 * turn is -90° about the face's outward normal, seen from outside), ray picking and
 * drag->move resolution for touch turns.
 */
class CubeGeometry private constructor(private val bodyHalf: Float) {

    /** One renderable chunk (plastic body or sticker) with flat normals and a flat color. */
    class MeshPart(
        val positions: FloatArray,
        val normals: FloatArray,
        val indices: ShortArray,
        /** RGBA in 0..1. */
        val color: FloatArray,
        /** Specular strength; stickers glossier than the plastic. */
        val gloss: Float,
    )

    class Cubie(
        /** Grid cell, each component in -1..1. */
        val grid: IntArray,
        /** Cube-space center at the idle pose ([spacing] per unit). */
        val center: FloatArray,
        val parts: List<MeshPart>,
    )

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
            val grid = intArrayOf(gx, gy, gz)
            val center = floatArrayOf(gx.toFloat(), gy.toFloat(), gz.toFloat())
            val parts = ArrayList<MeshPart>(4)
            parts.add(chamferedBox(bodyHalf, CHAMFER, PLASTIC, PLASTIC_GLOSS))
            for ((face, placement) in FACE_PLACEMENT) {
                val (axis, sign) = placement
                if (grid[axis] == sign) {
                    parts.add(stickerMesh(axis, sign, bodyHalf, STICKER_HALF, STICKER_RADIUS, FACE_COLORS[face]!!, STICKER_GLOSS))
                }
            }
            built.add(Cubie(grid, center, parts))
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
            val min = floatArrayOf(lc[0] - bodyHalf, lc[1] - bodyHalf, lc[2] - bodyHalf)
            val max = floatArrayOf(lc[0] + bodyHalf, lc[1] + bodyHalf, lc[2] + bodyHalf)
            val t = CubeMath.rayAabb(lo[0], lo[1], lo[2], ld[0], ld[1], ld[2], min, max) ?: continue
            if (t >= bestT) continue
            bestT = t
            // Struck face: the axis where the hit point sits closest to the box surface.
            val hit = floatArrayOf(lo[0] + ld[0] * t, lo[1] + ld[1] * t, lo[2] + ld[2] * t)
            var axis = 0
            var bestAxisDist = Float.MAX_VALUE
            for (a in 0 until 3) {
                val dist = bodyHalf - abs(hit[a] - lc[a])
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

    // ---- mesh construction -------------------------------------------------------------------

    /** Box with chamfered edges and corners: 6 inset faces, 12 bevel strips, 8 corner caps. */
    private fun chamferedBox(h: Float, c: Float, color: FloatArray, gloss: Float): MeshPart {
        val pos = ArrayList<Float>(96 * 3)
        val nrm = ArrayList<Float>(96 * 3)
        val idx = ArrayList<Short>(128)
        val w = h - c

        fun vertex(v: FloatArray, n: FloatArray): Short {
            pos.add(v[0]); pos.add(v[1]); pos.add(v[2])
            nrm.add(n[0]); nrm.add(n[1]); nrm.add(n[2])
            return (pos.size / 3 - 1).toShort()
        }

        // Inset face quads.
        for (axis in 0 until 3) for (sign in intArrayOf(1, -1)) {
            val n = FloatArray(3); n[axis] = sign.toFloat()
            val (a1, a2) = tangentAxes(axis)
            val v = FloatArray(3)
            fun corner(s1: Float, s2: Float): Short {
                v[axis] = sign * h; v[a1] = s1 * w; v[a2] = s2 * w
                return vertex(v, n)
            }
            val q0 = corner(-1f, -1f); val q1 = corner(1f, -1f); val q2 = corner(1f, 1f); val q3 = corner(-1f, 1f)
            idx.add(q0); idx.add(q1); idx.add(q2)
            idx.add(q0); idx.add(q2); idx.add(q3)
        }
        // Edge bevel strips (each runs along the third axis).
        for (a1 in 0 until 3) for (a2 in (a1 + 1) until 3) for (s1 in intArrayOf(1, -1)) for (s2 in intArrayOf(1, -1)) {
            val a3 = 3 - a1 - a2
            val n = FloatArray(3); n[a1] = s1.toFloat(); n[a2] = s2.toFloat()
            val len = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
            for (i in 0..2) n[i] /= len
            val v = FloatArray(3)
            fun corner(u1: Float, u2: Float, t: Float): Short {
                v[a1] = u1; v[a2] = u2; v[a3] = t
                return vertex(v, n)
            }
            val e0 = corner(s1 * h, s2 * w, -w); val e1 = corner(s1 * w, s2 * h, -w)
            val e2 = corner(s1 * w, s2 * h, w); val e3 = corner(s1 * h, s2 * w, w)
            idx.add(e0); idx.add(e1); idx.add(e2)
            idx.add(e0); idx.add(e2); idx.add(e3)
        }
        // Corner caps.
        for (s1 in intArrayOf(1, -1)) for (s2 in intArrayOf(1, -1)) for (s3 in intArrayOf(1, -1)) {
            val n = floatArrayOf(s1.toFloat(), s2.toFloat(), s3.toFloat())
            val len = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
            for (i in 0..2) n[i] /= len
            val p1 = vertex(floatArrayOf(s1 * h, s2 * w, s3 * w), n)
            val p2 = vertex(floatArrayOf(s1 * w, s2 * h, s3 * w), n)
            val p3 = vertex(floatArrayOf(s1 * w, s2 * w, s3 * h), n)
            idx.add(p1); idx.add(p2); idx.add(p3)
        }
        return MeshPart(pos.toFloatArray(), nrm.toFloatArray(), idx.toShortArray(), color, gloss)
    }

    /** Rounded-square sticker, raised [raise] above the face plane of a box with half [h]. */
    private fun stickerMesh(axis: Int, sign: Int, h: Float, half: Float, radius: Float, color: FloatArray, gloss: Float): MeshPart {
        val (a1, a2) = tangentAxes(axis)
        val plane = sign * (h + STICKER_RAISE)
        val n = FloatArray(3); n[axis] = sign.toFloat()
        val pos = ArrayList<Float>(17 * 3)
        val nrm = ArrayList<Float>(17 * 3)
        val idx = ArrayList<Short>(48)

        fun vertex(u1: Float, u2: Float): Short {
            val v = FloatArray(3)
            v[axis] = plane; v[a1] = u1; v[a2] = u2
            pos.add(v[0]); pos.add(v[1]); pos.add(v[2])
            nrm.add(n[0]); nrm.add(n[1]); nrm.add(n[2])
            return (pos.size / 3 - 1).toShort()
        }

        val centerIdx = vertex(0f, 0f)
        // Perimeter: four arcs of [ARC_SEGS] points; straight edges fall out of the fan.
        val ringIdx = ShortArray(4 * ARC_SEGS)
        val cornerDirs = arrayOf(floatArrayOf(1f, 1f), floatArrayOf(-1f, 1f), floatArrayOf(-1f, -1f), floatArrayOf(1f, -1f))
        for (k in 0 until 4) {
            val cx = cornerDirs[k][0] * (half - radius)
            val cy = cornerDirs[k][1] * (half - radius)
            val base = when (k) {
                0 -> 0f
                1 -> 90f
                2 -> 180f
                else -> 270f
            }
            for (j in 0 until ARC_SEGS) {
                val ang = Math.toRadians((base + 90.0 * j / ARC_SEGS).toDouble())
                ringIdx[k * ARC_SEGS + j] = vertex(cx + radius * cos(ang).toFloat(), cy + radius * sin(ang).toFloat())
            }
        }
        for (i in 0 until ringIdx.size) {
            idx.add(centerIdx); idx.add(ringIdx[i]); idx.add(ringIdx[(i + 1) % ringIdx.size])
        }
        return MeshPart(pos.toFloatArray(), nrm.toFloatArray(), idx.toShortArray(), color, gloss)
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

        /** Standard WCA sticker colors, tuned for the shader's lighting. */
        val FACE_COLORS: Map<CubeFace, FloatArray> = mapOf(
            CubeFace.U to floatArrayOf(0.93f, 0.94f, 0.96f, 1f),
            CubeFace.D to floatArrayOf(0.97f, 0.79f, 0.06f, 1f),
            CubeFace.F to floatArrayOf(0.12f, 0.62f, 0.24f, 1f),
            CubeFace.B to floatArrayOf(0.15f, 0.36f, 0.90f, 1f),
            CubeFace.R to floatArrayOf(0.86f, 0.11f, 0.13f, 1f),
            CubeFace.L to floatArrayOf(0.96f, 0.47f, 0.07f, 1f),
        )

        private val PLASTIC = floatArrayOf(0.055f, 0.06f, 0.075f, 1f)
        private const val PLASTIC_GLOSS = 0.16f
        private const val STICKER_GLOSS = 0.45f
        private const val CHAMFER = 0.055f
        private const val STICKER_HALF = 0.375f
        private const val STICKER_RADIUS = 0.095f
        private const val STICKER_RAISE = 0.0045f
        private const val ARC_SEGS = 4

        /** The cube: spacing 1, tight 3% seams, 27 cubies, 54 stickers. */
        fun procedural(): CubeGeometry = CubeGeometry(bodyHalf = 0.485f)

        /** The two axes != [axis], ascending. */
        private fun tangentAxes(axis: Int): Pair<Int, Int> = when (axis) {
            0 -> Pair(1, 2)
            1 -> Pair(0, 2)
            else -> Pair(0, 1)
        }
    }
}
