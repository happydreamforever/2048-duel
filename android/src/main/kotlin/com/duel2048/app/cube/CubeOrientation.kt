package com.duel2048.app.cube

import com.duel2048.shared.cube.CubeFace
import com.duel2048.shared.cube.CubeMove

/**
 * [CubeState] uses WCA white-up / green-front. MagicCube uses yellow-up / blue-front,
 * which is an x2 flip (swap U↔D and F↔B). Convert logical moves before driving the GL engine.
 */
object CubeOrientation {

    /** Map a logical WCA move to the equivalent move on the MagicCube orientation. */
    fun toVisualMove(move: CubeMove): CubeMove = when (move) {
        CubeMove.U -> CubeMove.Di
        CubeMove.Ui -> CubeMove.D
        CubeMove.U2 -> CubeMove.D2
        CubeMove.D -> CubeMove.Ui
        CubeMove.Di -> CubeMove.U
        CubeMove.D2 -> CubeMove.U2
        CubeMove.F -> CubeMove.Bi
        CubeMove.Fi -> CubeMove.B
        CubeMove.F2 -> CubeMove.B2
        CubeMove.B -> CubeMove.Fi
        CubeMove.Bi -> CubeMove.F
        CubeMove.B2 -> CubeMove.F2
        CubeMove.R, CubeMove.Ri, CubeMove.R2 -> move
        CubeMove.L, CubeMove.Li, CubeMove.L2 -> move
    }

    fun toVisualMoves(moves: List<CubeMove>): List<CubeMove> = moves.map { toVisualMove(it) }
}
