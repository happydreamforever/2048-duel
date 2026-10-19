package com.duel2048.shared

import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeFace
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
            val double = face.doubleTurn()
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

    @Test
    fun `quarter turns rotate the row-major face clockwise`() {
        val labelled = CubeState(CharArray(54) { ('A'.code + it).toChar() })
        val clockwise = listOf(6, 3, 0, 7, 4, 1, 8, 5, 2)
        for (move in CubeMove.QUARTER_TURNS) {
            val base = move.face().baseIndex
            val turned = labelled.apply(move)
            for (i in 0 until 9) {
                assertEquals(labelled.stickers[base + clockwise[i]], turned.stickers[base + i], move.notation())
            }
        }
    }

    @Test
    fun `turns keep each physical corner and edge together`() {
        val corners = listOf(
            listOf(8, 9, 20), listOf(6, 18, 38), listOf(0, 36, 47), listOf(2, 45, 11),
            listOf(29, 26, 15), listOf(27, 44, 24), listOf(33, 53, 42), listOf(35, 17, 51),
        )
        val edges = listOf(
            listOf(5, 10), listOf(7, 19), listOf(3, 37), listOf(1, 46),
            listOf(32, 16), listOf(28, 25), listOf(30, 43), listOf(34, 52),
            listOf(23, 12), listOf(21, 41), listOf(50, 39), listOf(48, 14),
        )
        val labelled = CubeState(CharArray(54) { it.toChar() })
        fun pieces(state: CubeState, positions: List<List<Int>>) =
            positions.map { piece -> piece.map { state.stickers[it] }.toSet() }.toSet()
        var state = labelled
        for (move in CubeMove.entries + CubeScramble.generate(42L)) {
            state = state.apply(move)
            assertEquals(pieces(labelled, corners), pieces(state, corners), move.notation())
            assertEquals(pieces(labelled, edges), pieces(state, edges), move.notation())
            for (face in CubeFace.entries) {
                assertEquals(labelled.stickers[face.baseIndex + 4], state.stickers[face.baseIndex + 4])
            }
        }
    }

    @Test
    fun `six repetitions of the right up commutator restore all stickers`() {
        val labelled = CubeState(CharArray(54) { it.toChar() })
        val algorithm = listOf(CubeMove.R, CubeMove.U, CubeMove.Ri, CubeMove.Ui)
        var state = labelled
        repeat(6) { state = state.applyAll(algorithm) }
        assertEquals(labelled.fingerprint(), state.fingerprint())
    }
}
