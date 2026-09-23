package com.duel2048.app.cube.magic.presentation.cube.engine

import com.duel2048.app.cube.magic.domain.math.MatrixMath
import com.duel2048.app.cube.magic.grafic.Cube
import com.duel2048.app.cube.magic.grafic.IMatrixTracker
import com.duel2048.app.cube.magic.presentation.cube.CubeDrawCommand

class CubeDrawCommandFactory(
    private val matrixMath: MatrixMath
) {
    fun createCommand(cube: Cube, projectionMatrix: FloatArray, matrixTracker: IMatrixTracker): CubeDrawCommand {
        val mvp = FloatArray(16)
        matrixMath.multiplyMM(mvp, 0, projectionMatrix, 0, matrixTracker.getMatrix(), 0)
        return CubeDrawCommand(cube, mvp)
    }
}
