package com.duel2048.app.cube.model3d

import com.duel2048.shared.cube.CubeMove

/** Drives a [RubiksCubeView] from shared WCA notation (same surface the old engines exposed). */
object CubeModelBridge {

    const val ASSET_PATH = "models/animated_rubiks_cube_modelvault3d.glb"

    fun configure(cube: RubiksCubeView, gesturesEnabled: Boolean) {
        cube.gesturesEnabled = gesturesEnabled
    }

    fun notationSequence(moves: List<CubeMove>): String =
        moves.joinToString(" ") { it.notation() }

    /** Reset to solved and play the whole scramble (white-up WCA). */
    fun applyScrambleAnimated(cube: RubiksCubeView, notations: List<String>) {
        if (notations.isEmpty()) return
        cube.renderer.applyScrambleAnimated(notations)
    }

    /** Animate a single competitive move (buttons and face drags both come through here). */
    fun applyMoveAnimated(cube: RubiksCubeView, move: CubeMove) {
        cube.renderer.applyMoveAnimated(move)
    }
}
