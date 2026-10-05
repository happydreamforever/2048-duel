package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeFace
import com.duel2048.shared.cube.CubeMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CubeModelMathTest {

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

    // ---- cube geometry (synthetic standard-color cube, spacing 2) ----------------------------

    private fun syntheticCube(): CubeGeometry {
        val half = 1f
        val materials = listOf(
            GlbModel.Material("black", floatArrayOf(0f, 0f, 0f, 1f)),
            GlbModel.Material("white", floatArrayOf(0.8f, 0.8f, 0.8f, 1f)), // U
            GlbModel.Material("yellow", floatArrayOf(0.8f, 0.68f, 0f, 1f)), // D
            GlbModel.Material("green", floatArrayOf(0f, 0.33f, 0f, 1f)), // F
            GlbModel.Material("blue", floatArrayOf(0f, 0.12f, 0.8f, 1f)), // B
            GlbModel.Material("red", floatArrayOf(0.8f, 0.03f, 0f, 1f)), // R
            GlbModel.Material("orange", floatArrayOf(0.8f, 0.18f, 0f, 1f)), // L
        )

        fun cubieMesh(gx: Int, gy: Int, gz: Int): GlbModel.Mesh {
            val c = floatArrayOf(gx * 2f, gy * 2f, gz * 2f)
            // Body: 8 corners, black.
            val bodyPos = ArrayList<Float>(24)
            val bodyNrm = ArrayList<Float>(24)
            val bodyIdx = ArrayList<Short>(36)
            var vi = 0
            for (sz in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) for (sx in intArrayOf(-1, 1)) {
                bodyPos.addAll(listOf(c[0] + sx * half, c[1] + sy * half, c[2] + sz * half))
                bodyNrm.addAll(listOf(sx.toFloat(), sy.toFloat(), sz.toFloat()))
                vi++
            }
            // A closed-enough index list (content does not matter for the math tests).
            for (i in 0 until 8) {
                bodyIdx.add(i.toShort()); bodyIdx.add(((i + 1) % 8).toShort()); bodyIdx.add(i.toShort())
            }
            val body = GlbModel.Primitive(
                positions = bodyPos.toFloatArray(),
                normals = bodyNrm.toFloatArray(),
                indices = bodyIdx.toShortArray(),
                material = 0,
            )
            // One sticker on the first outward direction so every axis/sign gets votes.
            val dirs = ArrayList<IntArray>()
            if (gx != 0) dirs.add(intArrayOf(gx, 0, 0))
            if (gy != 0) dirs.add(intArrayOf(0, gy, 0))
            if (gz != 0) dirs.add(intArrayOf(0, 0, gz))
            val prims = ArrayList<GlbModel.Primitive>()
            prims.add(body)
            for (dir in dirs) {
                val material = when {
                    dir[1] > 0 -> 1 // U white
                    dir[1] < 0 -> 2 // D yellow
                    dir[2] > 0 -> 3 // F green
                    dir[2] < 0 -> 4 // B blue
                    dir[0] > 0 -> 5 // R red
                    else -> 6 // L orange
                }
                val n = floatArrayOf(dir[0].toFloat(), dir[1].toFloat(), dir[2].toFloat())
                val p = floatArrayOf(
                    c[0] + dir[0] * half, c[1] + dir[1] * half, c[2] + dir[2] * half,
                    c[0] + dir[0] * half, c[1] + dir[1] * half, c[2] + dir[2] * half,
                    c[0] + dir[0] * half, c[1] + dir[1] * half, c[2] + dir[2] * half,
                )
                prims.add(
                    GlbModel.Primitive(
                        positions = p,
                        normals = floatArrayOf(n[0], n[1], n[2], n[0], n[1], n[2], n[0], n[1], n[2]),
                        indices = shortArrayOf(0, 1, 2),
                        material = material,
                    ),
                )
            }
            return GlbModel.Mesh("cubie", prims)
        }

        val nodes = ArrayList<GlbModel.Node>()
        val meshes = ArrayList<GlbModel.Mesh>()
        for (gz in intArrayOf(-1, 0, 1)) for (gy in intArrayOf(-1, 0, 1)) for (gx in intArrayOf(-1, 0, 1)) {
            meshes.add(cubieMesh(gx, gy, gz))
            nodes.add(GlbModel.Node("c$gx$gy$gz", meshes.size - 1, CubeMath.identity()))
        }
        val model = GlbModel(nodes, meshes, materials)
        return CubeGeometry(model)
    }

    @Test
    fun `grid spacing and face layers derive from the model`() {
        val g = syntheticCube()
        assertEquals(2f, g.spacing, 1e-5f)
        assertEquals(27, g.cubies.size)
        assertEquals(Pair(1, 1), g.faceLayer[CubeFace.U])
        assertEquals(Pair(1, -1), g.faceLayer[CubeFace.D])
        assertEquals(Pair(2, 1), g.faceLayer[CubeFace.F])
        assertEquals(Pair(2, -1), g.faceLayer[CubeFace.B])
        assertEquals(Pair(0, 1), g.faceLayer[CubeFace.R])
        assertEquals(Pair(0, -1), g.faceLayer[CubeFace.L])
        assertEquals(9, g.faceCubies(CubeFace.U).size)
    }

    @Test
    fun `wca turns map to signed quarter rotations`() {
        val g = syntheticCube()
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
        val g = syntheticCube()
        assertEquals(CubeMove.U, g.moveForTurn(1, 1, -90f))
        assertEquals(CubeMove.Ui, g.moveForTurn(1, 1, 90f))
        assertEquals(CubeMove.D, g.moveForTurn(1, -1, 90f))
        assertEquals(CubeMove.Ri, g.moveForTurn(0, 1, 90f))
        assertEquals(CubeMove.L, g.moveForTurn(0, -1, 90f))
        assertNull(g.moveForTurn(1, 0, 90f)) // middle slice has no move
    }

    @Test
    fun `picking finds the struck cubie and face`() {
        val g = syntheticCube()
        // Ray from +Z straight at the front-right-top corner cubie.
        val hit = g.pick(2f, 2f, 10f, 0f, 0f, -1f)!!
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
        val g = syntheticCube()
        val hit = CubeGeometry.PickHit(cubieIndex(g, 1, 0, 1), floatArrayOf(2f, 0f, 2f), 2, 1)
        // Screen: x right, y down. World +Y projects to screen up.
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertEquals(CubeMove.Ri, g.resolveDragMove(hit, 0f, -10f, project))
    }

    @Test
    fun `drag right the top-front row resolves to Ui`() {
        val g = syntheticCube()
        val hit = CubeGeometry.PickHit(cubieIndex(g, 1, 1, 1), floatArrayOf(2f, 2f, 2f), 2, 1)
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertEquals(CubeMove.Ui, g.resolveDragMove(hit, 10f, 0f, project))
    }

    @Test
    fun `middle layer drags do not resolve`() {
        val g = syntheticCube()
        // Front-center sticker, middle row: dragging right is an E-slice, no move.
        val hit = CubeGeometry.PickHit(cubieIndex(g, 0, 0, 1), floatArrayOf(0f, 0f, 2f), 2, 1)
        val project = { p: FloatArray -> floatArrayOf(p[0], -p[1]) }
        assertNull(g.resolveDragMove(hit, 10f, 0f, project))
    }

    private fun cubieIndex(g: CubeGeometry, x: Int, y: Int, z: Int): Int =
        g.cubies.indexOfFirst { it.grid[0] == x && it.grid[1] == y && it.grid[2] == z }
}
