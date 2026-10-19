package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeFace
import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeScramble
import com.duel2048.shared.cube.CubeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.math.abs

class CubeModelMathTest {

    private fun geometry(): CubeGeometry = CubeGeometry()

    // ---- matrix math -------------------------------------------------------------------------

    @Test
    fun `rotation is exact at quarter turns`() {
        val m = CubeMath.rotationAxis(0f, 1f, 0f, -90f)
        // U (normal turn) takes the front face to the left: +Z -> -X.
        val p = CubeMath.transformPoint(m, 0f, 0f, 1f)
        assertTrue(abs(p[0] + 1f) < 1e-5f)
        assertTrue(abs(p[2]) < 1e-5f)
    }

    @Test
    fun `inverse roundtrip restores identity`() {
        val m = CubeMath.multiply(
            CubeMath.rotationAxis(1f, 2f, -1f, 37f),
            CubeMath.translation(3f, -4f, 0.5f),
        )
        val inv = CubeMath.inverse(m)!!
        val product = CubeMath.multiply(m, inv)
        for (i in 0 until 16) {
            val expected = if (i % 5 == 0) 1f else 0f
            assertTrue(abs(product[i] - expected) < 1e-4f)
        }
    }

    @Test
    fun `ray aabb hit and miss`() {
        val min = floatArrayOf(-1f, -1f, -1f)
        val max = floatArrayOf(1f, 1f, 1f)
        assertEquals(4f, CubeMath.rayAabb(0f, 0f, 5f, 0f, 0f, -1f, min, max)!!, 1e-6f)
        assertNull(CubeMath.rayAabb(0f, 0f, 5f, 0f, 0f, 1f, min, max)) // box behind the ray
        assertNull(CubeMath.rayAabb(5f, 0f, 5f, 0f, 0f, -1f, min, max)) // misses sideways
    }

    // ---- the hand-built cube -----------------------------------------------------------------

    @Test
    fun `grid and faces are WCA-native`() {
        val g = geometry()
        assertEquals(1f, g.spacing, 1e-5f)
        assertEquals(27, g.cubies.size)
        assertEquals(Pair(1, 1), g.faceLayer[CubeFace.U])
        assertEquals(Pair(1, -1), g.faceLayer[CubeFace.D])
        assertEquals(Pair(2, 1), g.faceLayer[CubeFace.F])
        assertEquals(Pair(2, -1), g.faceLayer[CubeFace.B])
        assertEquals(Pair(0, 1), g.faceLayer[CubeFace.R])
        assertEquals(Pair(0, -1), g.faceLayer[CubeFace.L])
        assertEquals(9, g.faceCubies(CubeFace.U).size)
        assertEquals("cubie_1_-1_0", g.cubies[cubieIndex(g, 1, -1, 0)].nodeName)
    }

    // ---- animation ---------------------------------------------------------------------------

    @Test
    fun `scramble then inverse returns every cubie exactly home`() {
        var now = 0L
        val animator = CubeAnimator(geometry()) { now }
        val scramble = listOf("R", "U2", "F'", "L", "D", "B2", "R'", "U")
        animator.applyScramble(scramble)
        for (notation in scramble.reversed()) {
            val move = CubeMove.parse(notation)!!
            animator.applyMove(CubeMove.parse(inverse(move.notation()))!!)
        }
        val out = Array(27) { FloatArray(16) }
        var guard = 0
        while (animator.frame(out)) {
            now += 37
            check(++guard < 10_000)
        }
        for (i in 0 until 27) {
            val pose = animator.poseOf(i)
            for (k in 0 until 16) assertEquals(if (k % 5 == 0) 1f else 0f, pose[k], 0f) // exact, not approximate
        }
    }

    @Test
    fun `queued turns select cubies from their current positions`() {
        var now = 0L
        val g = geometry()
        val animator = CubeAnimator(g) { now }
        animator.applyMove(CubeMove.U)
        animator.applyMove(CubeMove.R)
        val out = Array(27) { FloatArray(16) }
        while (animator.frame(out)) now += 37

        // U moves this original back-left cubie into the right layer, so the following R
        // must rotate it too. Selecting R from the original grid position misses it.
        val movedIntoRight = cubieIndex(g, -1, 1, -1)
        val expected = CubeMath.multiply(
            CubeMath.rotationAxis(1f, 0f, 0f, -90f),
            CubeMath.rotationAxis(0f, 1f, 0f, -90f),
        )
        val actual = animator.poseOf(movedIntoRight)
        for (i in 0 until 16) assertEquals(expected[i], actual[i], 0f)

        // This one starts on the right but leaves it during U, so R must not rotate it.
        val movedOutOfRight = cubieIndex(g, 1, 1, 1)
        val uOnly = CubeMath.rotationAxis(0f, 1f, 0f, -90f)
        val outPose = animator.poseOf(movedOutOfRight)
        for (i in 0 until 16) assertEquals(uOnly[i], outPose[i], 0f)
    }

    @Test
    fun `rendered stickers match shared state through every move and mixed scrambles`() {
        var now = 0L
        val g = geometry()
        val animator = CubeAnimator(g) { now }
        val out = Array(27) { FloatArray(16) }
        val labelled = CubeState(CharArray(54) { it.toChar() })

        fun assertSequence(moves: List<CubeMove>) {
            animator.reset()
            var state = labelled.copyState()
            for ((step, move) in moves.withIndex()) {
                animator.applyMove(move)
                animator.frame(out)
                now += 1000
                animator.frame(out)
                state = state.apply(move)

                val rendered = CharArray(54)
                val occupied = mutableSetOf<Int>()
                for (sticker in 0 until 54) {
                    val (p, n) = stickerLocation(sticker)
                    val cubie = cubieIndex(g, p[0].toInt(), p[1].toInt(), p[2].toInt())
                    val pose = animator.poseOf(cubie)
                    val position = CubeMath.transformPoint(pose, p[0], p[1], p[2])
                    val normal = CubeMath.transformDir(pose, n[0], n[1], n[2])
                    val destination = (0 until 54).single { index ->
                        val (expectedP, expectedN) = stickerLocation(index)
                        (0 until 3).all { position[it] == expectedP[it] && normal[it] == expectedN[it] }
                    }
                    assertTrue("two stickers overlap after $step ${move.notation()}", occupied.add(destination))
                    rendered[destination] = sticker.toChar()
                }
                assertEquals("step $step ${move.notation()}", state.fingerprint(), rendered.concatToString())
            }
        }

        for (move in CubeMove.entries) assertSequence(listOf(move))
        for (seed in 0L until 5L) {
            val scramble = CubeScramble.generate(seed)
            assertSequence(scramble + CubeScramble.inverse(scramble))
        }
    }

    @Test
    fun `frame places cubies at pose times grid center`() {
        val animator = CubeAnimator(geometry()) { 0L }
        val out = Array(27) { FloatArray(16) }
        animator.frame(out)
        val i = cubieIndex(animator.geometry, 1, -1, 0)
        assertEquals(1f, out[i][12], 0f)
        assertEquals(-1f, out[i][13], 0f)
        assertEquals(0f, out[i][14], 0f)
    }

    // ---- the shipped model -------------------------------------------------------------------

    @Test
    fun `model asset matches the layout and WCA colors`() {
        val bytes = java.io.File("src/main/assets/models/rubiks_cube.glb").readBytes()
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, buf.getInt(0))
        val jsonLen = buf.getInt(12)
        val gltf = Json.parseToJsonElement(String(bytes, 20, jsonLen, Charsets.UTF_8)).jsonObject
        val nodes = gltf["nodes"]!!.jsonArray.map { it.jsonObject }
        val meshes = gltf["meshes"]!!.jsonArray.map { it.jsonObject }
        val materials = gltf["materials"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        val accessors = gltf["accessors"]!!.jsonArray.map { it.jsonObject }
        val g = geometry()
        for (cubie in g.cubies) {
            val node = nodes.single { it["name"]?.jsonPrimitive?.content == cubie.nodeName }
            val t = node["translation"]!!.jsonArray.map { it.jsonPrimitive.float }
            for (a in 0 until 3) assertEquals(cubie.grid[a].toFloat(), t[a], 0f)
            val prims = meshes[node["mesh"]!!.jsonPrimitive.int]["primitives"]!!.jsonArray.map { it.jsonObject }
            val tiles = prims.map { materials[it["material"]!!.jsonPrimitive.int] }.filter { it.startsWith("tile_") }
            val expected = g.faceLayer.filter { (_, layer) -> cubie.grid[layer.first] == layer.second }.keys.map { "tile_" + it.name }
            assertEquals(expected.sorted(), tiles.sorted())
            for (prim in prims) {
                val material = materials[prim["material"]!!.jsonPrimitive.int]
                if (!material.startsWith("tile_")) continue
                val (axis, sign) = g.faceLayer[CubeFace.valueOf(material.removePrefix("tile_"))]!!
                val pos = accessors[prim["attributes"]!!.jsonObject["POSITION"]!!.jsonPrimitive.int]
                val lo = pos["min"]!!.jsonArray[axis].jsonPrimitive.float
                val hi = pos["max"]!!.jsonArray[axis].jsonPrimitive.float
                // Flat tile on the outer face of its cubie (local space), just above the body.
                assertEquals(lo, hi, 1e-6f)
                assertTrue(lo * sign > 0.5f && lo * sign < 0.52f)
            }
        }
    }

    private fun inverse(notation: String): String = when {
        notation.endsWith("2") -> notation
        notation.endsWith("'") -> notation.dropLast(1)
        else -> "$notation'"
    }

    @Test
    fun `wca turns map to signed quarter rotations`() {
        val g = geometry()
        val u = g.turnFor(CubeMove.U)
        assertEquals(1, u.axis)
        assertEquals(1, u.coord)
        assertEquals(-90f, u.degrees, 1e-5f)
        // D turns the other way about +Y (clockwise seen from below).
        assertEquals(90f, g.turnFor(CubeMove.D).degrees, 1e-5f)
        // Doubles animate 180.
        assertEquals(180f, g.turnFor(CubeMove.R2).degrees, 1e-5f)
        // Primes take the short path to the same place as three quarters.
        assertEquals(90f, g.turnFor(CubeMove.Ui).degrees, 1e-5f)
    }

    @Test
    fun `layer rotation maps back to moves`() {
        val g = geometry()
        assertEquals(CubeMove.U, g.moveForTurn(1, 1, -90f))
        assertEquals(CubeMove.Ui, g.moveForTurn(1, 1, 90f))
        assertEquals(CubeMove.D, g.moveForTurn(1, -1, 90f))
        assertEquals(CubeMove.Ri, g.moveForTurn(0, 1, 90f))
        assertEquals(CubeMove.L, g.moveForTurn(0, -1, 90f))
        assertNull(g.moveForTurn(1, 0, 90f)) // middle slice has no move
    }

    @Test
    fun `picking finds the struck cubie and face`() {
        val g = geometry()
        // Ray from +Z straight at the front-right-top corner cubie (center (1,1,1)).
        val hit = g.pick(1f, 1f, 10f, 0f, 0f, -1f)!!
        val cubie = g.cubies[hit.cubie]
        assertEquals(1, cubie.grid[0])
        assertEquals(1, cubie.grid[1])
        assertEquals(1, cubie.grid[2])
        assertEquals(2, hit.normalAxis)
        assertEquals(1, hit.normalSign)
        // Missing ray.
        assertNull(g.pick(50f, 50f, 10f, 0f, 0f, -1f))
    }

    @Test
    fun `picking follows cubies after mixed turns on all six faces`() {
        var now = 0L
        val g = geometry()
        val animator = CubeAnimator(g) { now }
        animator.applyScramble(listOf("R", "U", "F'", "L2", "D", "B"))
        val out = Array(27) { FloatArray(16) }
        while (animator.frame(out)) now += 37

        for (sticker in 0 until 54) {
            val (p, n) = stickerLocation(sticker)
            val expectedCubie = g.cubies.indices.single { i ->
                val c = g.cubies[i].center
                val current = CubeMath.transformPoint(animator.poseOf(i), c[0], c[1], c[2])
                (0 until 3).all { current[it] == p[it] }
            }
            val origin = FloatArray(3) { p[it] + n[it] * 5f }
            val hit = animator.pick(origin, FloatArray(3) { -n[it] })!!
            assertEquals("sticker $sticker", expectedCubie, hit.cubie)
            val axis = n.indexOfFirst { it != 0f }
            assertEquals(axis, hit.normalAxis)
            assertEquals(n[axis].toInt(), hit.normalSign)
            for (a in 0 until 3) assertEquals(p[a] + n[a] * 0.5f, hit.point[a], 1e-5f)
        }
    }

    @Test
    fun `opposite drags on the front-right column resolve to opposite turns`() {
        val g = geometry()
        val hit = CubeGeometry.PickHit(cubieIndex(g, 1, 0, 1), floatArrayOf(1f, 0f, 1f), 2, 1)
        // Screen: x right, y down. World +Y projects to screen up.
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertEquals(CubeMove.R, g.resolveDragMove(hit, 0f, -10f, project))
        assertEquals(CubeMove.Ri, g.resolveDragMove(hit, 0f, 10f, project))
    }

    @Test
    fun `drag right the top-front row resolves to Ui`() {
        val g = geometry()
        val hit = CubeGeometry.PickHit(cubieIndex(g, 1, 1, 1), floatArrayOf(1f, 1f, 1f), 2, 1)
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertEquals(CubeMove.Ui, g.resolveDragMove(hit, 10f, 0f, project))
        assertEquals(CubeMove.U, g.resolveDragMove(hit, -10f, 0f, project))
    }

    @Test
    fun `middle layer drags do not resolve`() {
        val g = geometry()
        // Front-center sticker, middle row: dragging right is an E-slice, no move.
        val hit = CubeGeometry.PickHit(cubieIndex(g, 0, 0, 1), floatArrayOf(0f, 0f, 1f), 2, 1)
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertNull(g.resolveDragMove(hit, 10f, 0f, project))
    }

    private fun cubieIndex(g: CubeGeometry, x: Int, y: Int, z: Int): Int =
        g.cubies.indexOfFirst { it.grid[0] == x && it.grid[1] == y && it.grid[2] == z }

    /** URFDLB row-major stickers, looking at each face from outside (U at the top of F). */
    private fun stickerLocation(index: Int): Pair<FloatArray, FloatArray> {
        val a = (index % 3 - 1).toFloat()
        val b = (1 - index % 9 / 3).toFloat()
        return when (CubeFace.entries[index / 9]) {
            CubeFace.U -> floatArrayOf(a, 1f, -b) to floatArrayOf(0f, 1f, 0f)
            CubeFace.R -> floatArrayOf(1f, b, -a) to floatArrayOf(1f, 0f, 0f)
            CubeFace.F -> floatArrayOf(a, b, 1f) to floatArrayOf(0f, 0f, 1f)
            CubeFace.D -> floatArrayOf(a, -1f, b) to floatArrayOf(0f, -1f, 0f)
            CubeFace.L -> floatArrayOf(-1f, b, a) to floatArrayOf(-1f, 0f, 0f)
            CubeFace.B -> floatArrayOf(-a, b, -1f) to floatArrayOf(0f, 0f, -1f)
        }
    }
}
