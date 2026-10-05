package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeFace
import com.duel2048.shared.cube.CubeMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CubeModelMathTest {

    private fun geometry(): CubeGeometry = CubeGeometry.procedural()

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
    fun `grid faces and stickers are WCA-native`() {
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

        // Corner cubie: black body + white U + red R + green F stickers.
        val corner = g.cubies[cubieIndex(g, 1, 1, 1)]
        assertEquals(4, corner.parts.size)
        val stickers = corner.parts.drop(1)
        assertTrue(stickers.any { sameColor(it.color, CubeGeometry.FACE_COLORS[CubeFace.U]!!) })
        assertTrue(stickers.any { sameColor(it.color, CubeGeometry.FACE_COLORS[CubeFace.R]!!) })
        assertTrue(stickers.any { sameColor(it.color, CubeGeometry.FACE_COLORS[CubeFace.F]!!) })
        // Center cubie: bare plastic, no stickers at all.
        assertEquals(1, g.cubies[cubieIndex(g, 0, 0, 0)].parts.size)
        // 54 stickers across the cube.
        assertEquals(54, g.cubies.sumOf { it.parts.size - 1 })
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
    fun `drag up the front-right column resolves to Ri`() {
        val g = geometry()
        val hit = CubeGeometry.PickHit(cubieIndex(g, 1, 0, 1), floatArrayOf(1f, 0f, 1f), 2, 1)
        // Screen: x right, y down. World +Y projects to screen up.
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertEquals(CubeMove.Ri, g.resolveDragMove(hit, 0f, -10f, project))
    }

    @Test
    fun `drag right the top-front row resolves to Ui`() {
        val g = geometry()
        val hit = CubeGeometry.PickHit(cubieIndex(g, 1, 1, 1), floatArrayOf(1f, 1f, 1f), 2, 1)
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertEquals(CubeMove.Ui, g.resolveDragMove(hit, 10f, 0f, project))
    }

    @Test
    fun `middle layer drags do not resolve`() {
        val g = geometry()
        // Front-center sticker, middle row: dragging right is an E-slice, no move.
        val hit = CubeGeometry.PickHit(cubieIndex(g, 0, 0, 1), floatArrayOf(0f, 0f, 1f), 2, 1)
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertNull(g.resolveDragMove(hit, 10f, 0f, project))
    }

    private fun sameColor(a: FloatArray, b: FloatArray): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) if (abs(a[i] - b[i]) > 1e-6f) return false
        return true
    }

    private fun cubieIndex(g: CubeGeometry, x: Int, y: Int, z: Int): Int =
        g.cubies.indexOfFirst { it.grid[0] == x && it.grid[1] == y && it.grid[2] == z }
}
