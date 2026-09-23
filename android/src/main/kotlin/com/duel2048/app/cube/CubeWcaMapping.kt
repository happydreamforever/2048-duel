package com.duel2048.app.cube

import com.duel2048.app.cube.magic.grafic.ActiveSlice
import com.duel2048.shared.cube.CubeMove

/** Maps WCA moves to MagicCube [ActiveSlice] turns (yellow up, blue front). */
object CubeWcaMapping {

    data class SliceTurn(val slice: ActiveSlice, val direction: Int)

    fun toSliceTurn(move: CubeMove): SliceTurn = when (move) {
        CubeMove.U, CubeMove.U2 -> SliceTurn(ActiveSlice.ROTATION_AXIS_Y_2, 1)
        CubeMove.Ui -> SliceTurn(ActiveSlice.ROTATION_AXIS_Y_2, -1)
        CubeMove.D, CubeMove.D2 -> SliceTurn(ActiveSlice.ROTATION_AXIS_Y_0, -1)
        CubeMove.Di -> SliceTurn(ActiveSlice.ROTATION_AXIS_Y_0, 1)
        CubeMove.R, CubeMove.R2 -> SliceTurn(ActiveSlice.ROTATION_AXIS_X_2, -1)
        CubeMove.Ri -> SliceTurn(ActiveSlice.ROTATION_AXIS_X_2, 1)
        CubeMove.L, CubeMove.L2 -> SliceTurn(ActiveSlice.ROTATION_AXIS_X_0, 1)
        CubeMove.Li -> SliceTurn(ActiveSlice.ROTATION_AXIS_X_0, -1)
        CubeMove.F, CubeMove.F2 -> SliceTurn(ActiveSlice.ROTATION_AXIS_Z_2, -1)
        CubeMove.Fi -> SliceTurn(ActiveSlice.ROTATION_AXIS_Z_2, 1)
        CubeMove.B, CubeMove.B2 -> SliceTurn(ActiveSlice.ROTATION_AXIS_Z_0, 1)
        CubeMove.Bi -> SliceTurn(ActiveSlice.ROTATION_AXIS_Z_0, -1)
    }

    fun applyToEngine(engine: com.duel2048.app.cube.magic.grafic.ICubeGameEngine, move: CubeMove) {
        val turn = toSliceTurn(move)
        repeat(move.quarterTurns()) {
            engine.applyQuarterTurn(turn.slice, turn.direction)
        }
    }
}
