package com.duel2048.app.cube2

import com.catalinjurjiu.animcubeandroid.AnimCube
import com.duel2048.shared.cube.CubeMove

/** Helpers for driving AnimCube (cube2) from shared WCA notation. */
object Cube2Bridge {

    fun configure(cube: AnimCube) {
        cube.setBackFacesDistance(4)
        cube.setEditable(false)
        cube.setSingleRotationSpeed(8)
        cube.setDoubleRotationSpeed(8)
    }

    fun notationSequence(moves: List<CubeMove>): String =
        moves.joinToString(" ") { it.notation() }

    /** Animate the full scramble (white-up WCA; matches AnimCube default orientation). */
    fun applyScrambleAnimated(cube: AnimCube, notations: List<String>) {
        val moves = notations.mapNotNull { CubeMove.parse(it) }
        if (moves.isEmpty()) return
        cube.stopAnimation()
        cube.setMoveSequence(notationSequence(moves))
        cube.animateMoveSequence()
    }

    /** Animate a single competitive move from the move buttons. */
    fun applyMoveAnimated(cube: AnimCube, move: CubeMove) {
        cube.stopAnimation()
        cube.setMoveSequence(move.notation())
        cube.animateMove()
    }
}
