package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeFace
import com.duel2048.shared.cube.CubeMove
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns a parsed Rubik's-cube glb (one node per cubie, flat sticker colors) into the
 * structures the renderer needs:
 *
 *  - the 27 cubies with their grid cell, derived from world bounding-box centers
 *    (the exported nodes all carry identity transforms, geometry is baked in cube space),
 *  - which world axis/sign each WCA face lives on, derived from the sticker colors
 *    (white = U, yellow = D, green = F, blue = B, red = R, orange = L), so moves look
 *    right no matter how the model was oriented in the 3D editor,
 *  - the exact axis/angle a move animates (WCA: a face turn is -90° about the face's
 *    outward normal, seen from outside the face),
 *  - ray picking and drag->move resolution for touch turns.
 */
class CubeGeometry(private val model: GlbModel) {

    class Cubie(
        /** Node index in the glb (cubies are the mesh-bearing nodes, in order). */
        val node: Int,
        /** Grid cell, each component in -1..1. */
        val grid: IntArray,
        /** World-space bbox center (idle pose). */
        val center: FloatArray,
        /** Raw-local (pre node transform) bounding box of the whole cubie. */
        val localMin: FloatArray,
        val localMax: FloatArray,
        val primitives: List<Int>,
    )

    /** One turn to animate: a layer's cubies rotate about the world +axis by [degrees]. */
    class TurnSpec(val axis: Int, val coord: Int, val degrees: Float, val members: List<Int>)

    /** A touch hit on a cubie, in cube space (orbit undone, idle pose). */
    class PickHit(val cubie: Int, val point: FloatArray, val normalAxis: Int, val normalSign: Int)

    val cubies: List<Cubie>
    /** Center-to-center distance between neighbouring cubies (world units). */
    val spacing: Float

    /** WCA face -> (axis 0..2, grid sign -1/+1) of its outer layer. */
    val faceLayer: Map<CubeFace, Pair<Int, Int>>

    init {
        val cubieNodeIndices = model.nodes.withIndex().filter { it.value.mesh >= 0 }.map { it.index }
        var maxAbs = 0f
        val centers = ArrayList<FloatArray>(27)
        val locals = ArrayList<Triple<FloatArray, FloatArray, List<Int>>>(27)
        for (node in model.nodes) {
            if (node.mesh < 0) continue
            val mesh = model.meshes[node.mesh]
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            val prims = ArrayList<Int>()
            for ((pIndex, prim) in mesh.primitives.withIndex()) {
                prims.add(pIndex)
                var i = 0
                while (i < prim.positions.size) {
                    val p = CubeMath.transformPoint(node.transform, prim.positions[i], prim.positions[i + 1], prim.positions[i + 2])
                    if (p[0] < minX) minX = p[0]; if (p[0] > maxX) maxX = p[0]
                    if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1]
                    if (p[2] < minZ) minZ = p[2]; if (p[2] > maxZ) maxZ = p[2]
                    i += 3
                }
            }
            // Raw-local bbox: undo the node transform (identity for this model, but be correct).
            val inv = CubeMath.inverse(node.transform) ?: CubeMath.identity()
            val lmin = CubeMath.transformPoint(inv, minX, minY, minZ)
            val lmax = CubeMath.transformPoint(inv, maxX, maxY, maxZ)
            val c = floatArrayOf((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f)
            centers.add(c)
            locals.add(Triple(floatArrayOf(min(lmin[0], lmax[0]), min(lmin[1], lmax[1]), min(lmin[2], lmax[2])),
                floatArrayOf(max(lmin[0], lmax[0]), max(lmin[1], lmax[1]), max(lmin[2], lmax[2])), prims))
            for (i in 0 until 3) if (abs(c[i]) > maxAbs) maxAbs = abs(c[i])
        }
        check(centers.size == 27) { "expected 27 cubie nodes, got ${centers.size}" }
        check(maxAbs > 0f) { "degenerate cube geometry" }
        spacing = maxAbs

        cubies = centers.mapIndexed { index, c ->
            val (lmin, lmax, prims) = locals[index]
            Cubie(
                node = cubieNodeIndices[index],
                grid = intArrayOf((c[0] / spacing).roundToInt(), (c[1] / spacing).roundToInt(), (c[2] / spacing).roundToInt()),
                center = c,
                localMin = lmin,
                localMax = lmax,
                primitives = prims,
            )
        }
        check(cubies.count { it.grid[0] == 0 && it.grid[1] == 0 && it.grid[2] == 0 } == 1) { "cube center cubie missing" }

        faceLayer = deriveFaceLayers()
    }

    /** Cubie index -> node index (cubies are the mesh-bearing nodes, in order). */
    fun nodeOf(cubie: Int): Int = cubies[cubie].node

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
     * cubie's current accumulated layer-turn matrix (identity when idle); gestures only act
     * when idle, so normals are axis-aligned in cube space either way.
     */
    fun pick(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, poseOf: (Int) -> FloatArray = { CubeMath.identity() }): PickHit? {
        var bestT = Float.MAX_VALUE
        var best: PickHit? = null
        for ((cubieIndex, cubie) in cubies.withIndex()) {
            val node = model.nodes[nodeOf(cubieIndex)]
            val pose = poseOf(cubieIndex)
            val nodePose = CubeMath.multiply(pose, node.transform)
            val inv = CubeMath.inverse(nodePose) ?: continue
            val lo = CubeMath.transformPoint(inv, ox, oy, oz)
            val ld = CubeMath.transformDir(inv, dx, dy, dz)
            val t = CubeMath.rayAabb(lo[0], lo[1], lo[2], ld[0], ld[1], ld[2], cubie.localMin, cubie.localMax) ?: continue
            if (t >= bestT) continue
            bestT = t
            // Struck face of the raw-local box...
            val localHit = floatArrayOf(lo[0] + ld[0] * t, lo[1] + ld[1] * t, lo[2] + ld[2] * t)
            val localCenter = floatArrayOf(
                (cubie.localMin[0] + cubie.localMax[0]) / 2f,
                (cubie.localMin[1] + cubie.localMax[1]) / 2f,
                (cubie.localMin[2] + cubie.localMax[2]) / 2f,
            )
            var axis = 0
            var bestAxisDist = Float.MAX_VALUE
            for (a in 0 until 3) {
                val half = (cubie.localMax[a] - cubie.localMin[a]) / 2f
                val dist = half - abs(localHit[a] - localCenter[a])
                if (dist < bestAxisDist) { bestAxisDist = dist; axis = a }
            }
            val localSign = if (localHit[axis] >= localCenter[axis]) 1 else -1
            // ...then back to cube space (rotations keep it axis-aligned; snap numerics).
            val nLocal = FloatArray(3)
            nLocal[axis] = localSign.toFloat()
            val nCube = CubeMath.transformDir(nodePose, nLocal[0], nLocal[1], nLocal[2])
            var cubeAxis = 0
            var maxComp = 0f
            var cubeSign = 1
            for (a in 0 until 3) if (abs(nCube[a]) > maxComp) { maxComp = abs(nCube[a]); cubeAxis = a; cubeSign = if (nCube[a] >= 0f) 1 else -1 }
            val hitCube = CubeMath.transformPoint(nodePose, localHit[0], localHit[1], localHit[2])
            best = PickHit(cubieIndex, hitCube, cubeAxis, cubeSign)
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

    // ---- internals ---------------------------------------------------------------------------

    /** WCA face -> model axis placement, by nearest-palette sticker color voting. */
    private fun deriveFaceLayers(): Map<CubeFace, Pair<Int, Int>> {
        val palette = mapOf(
            CubeFace.U to floatArrayOf(0.80f, 0.80f, 0.80f), // white
            CubeFace.D to floatArrayOf(0.80f, 0.68f, 0.00f), // yellow
            CubeFace.F to floatArrayOf(0.00f, 0.33f, 0.00f), // green
            CubeFace.B to floatArrayOf(0.00f, 0.12f, 0.80f), // blue
            CubeFace.R to floatArrayOf(0.80f, 0.03f, 0.00f), // red
            CubeFace.L to floatArrayOf(0.80f, 0.18f, 0.00f), // orange
        )

        // Vote per material: sum the |normal| components along the dominant axis of each
        // sticker primitive (black plastic body excluded).
        data class Vote(var axis: Int, var sign: Int, var weight: Float)
        val votes = HashMap<Int, Vote>()
        for ((cubieIndex, cubie) in cubies.withIndex()) {
            val mesh = model.meshes[model.nodes[nodeOf(cubieIndex)].mesh]
            for (pIndex in cubie.primitives) {
                val prim = mesh.primitives[pIndex]
                val color = model.materials[prim.material].color
                if (color[0] < 0.1f && color[1] < 0.1f && color[2] < 0.1f) continue
                var sx = 0f; var sy = 0f; var sz = 0f
                var ssigned = 0f
                var i = 0
                while (i < prim.normals.size) {
                    sx += abs(prim.normals[i]); sy += abs(prim.normals[i + 1]); sz += abs(prim.normals[i + 2])
                    i += 3
                }
                val axis = when {
                    sx >= sy && sx >= sz -> 0
                    sy >= sz -> 1
                    else -> 2
                }
                var j = axis
                while (j < prim.normals.size) { ssigned += prim.normals[j]; j += 3 }
                val sign = if (ssigned >= 0f) 1 else -1
                val weight = when (axis) { 0 -> sx; 1 -> sy; else -> sz }
                val existing = votes[prim.material]
                if (existing == null || weight > existing.weight) votes[prim.material] = Vote(axis, sign, weight)
            }
        }

        val result = HashMap<CubeFace, Pair<Int, Int>>()
        val used = HashSet<Int>()
        for ((face, target) in palette) {
            var bestMaterial = -1
            var bestDist = Float.MAX_VALUE
            for ((material, _) in votes) {
                if (material in used) continue
                val c = model.materials[material].color
                val d = (c[0] - target[0]) * (c[0] - target[0]) + (c[1] - target[1]) * (c[1] - target[1]) + (c[2] - target[2]) * (c[2] - target[2])
                if (d < bestDist) { bestDist = d; bestMaterial = material }
            }
            check(bestMaterial >= 0) { "could not map WCA face $face to a model material" }
            used.add(bestMaterial)
            val vote = checkNotNull(votes[bestMaterial])
            result[face] = Pair(vote.axis, vote.sign)
        }
        check(result.values.toSet().size == 6) { "two WCA faces mapped to the same axis placement" }
        return result
    }

    private fun min(a: Float, b: Float) = if (a < b) a else b
    private fun max(a: Float, b: Float) = if (a > b) a else b
}
