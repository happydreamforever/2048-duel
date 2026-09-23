package com.duel2048.shared

import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeScramble
import com.duel2048.shared.cube.CubeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CubeStateTest {

    @Test
    fun `solved cube stays solved after identity cycle`() {
        assertTrue(CubeState.SOLVED.isSolved())
    }

    @Test
    fun `move and inverse return to solved`() {
        for (move in CubeMove.entries) {
            val scrambled = CubeState.SOLVED.apply(move)
            assertFalse(scrambled.isSolved(), move.notation())
            val restored = scrambled.apply(move.inverse())
            assertTrue(restored.isSolved(), move.notation())
        }
    }

    @Test
    fun `double turn is self-inverse`() {
        for (face in CubeMove.QUARTER_TURNS) {
            val double = when (face) {
                CubeMove.U -> CubeMove.U2
                CubeMove.R -> CubeMove.R2
                CubeMove.F -> CubeMove.F2
                CubeMove.D -> CubeMove.D2
                CubeMove.L -> CubeMove.L2
                CubeMove.B -> CubeMove.B2
            }
            val once = CubeState.SOLVED.apply(double)
            val twice = once.apply(double)
            assertTrue(twice.isSolved(), double.notation())
        }
    }

    @Test
    fun `scramble is deterministic from seed`() {
        val a = CubeScramble.generate(42L)
        val b = CubeScramble.generate(42L)
        assertEquals(a, b)
        assertFalse(CubeScramble.apply(42L).isSolved())
        assertTrue(CubeScramble.apply(42L).applyAll(CubeScramble.inverse(a)).isSolved())
    }

    @Test
    fun `notation round trip`() {
        val moves = CubeScramble.generate(7L)
        val text = moves.joinToString(" ") { it.notation() }
        val parsed = CubeMove.parseList(text)
        assertEquals(moves, parsed)
    }
}
