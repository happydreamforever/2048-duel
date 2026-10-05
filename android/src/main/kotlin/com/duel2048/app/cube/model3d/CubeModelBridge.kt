package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeMove

/** Drives a [RubiksCubeView] from shared WCA notation (same surface the old engines exposed). */
object CubeModelBridge {

    fun configure(cube: RubiksCubeView, gesturesEnabled: Boolean) {
        cube.gesturesEnabled = gesturesEnabled
    }

    fun notationSequence(moves: List<CubeMove>): String =
        moves.joinToString(" ") { it.notation() }

    /** Reset to solved and play the whole scramble (white-up WCA). */
    fun applyScrambleAnimated(cube: RubiksCubeView, notations: List<String>) {
        if (notations.isEmpty()) return
        cube.animator.applyScramble(notations)
    }

    /** Animate a single competitive move (face drags and opponent moves both come through here). */
    fun applyMoveAnimated(cube: RubiksCubeView, move: CubeMove) {
        cube.animator.applyMove(move)
    }
}
