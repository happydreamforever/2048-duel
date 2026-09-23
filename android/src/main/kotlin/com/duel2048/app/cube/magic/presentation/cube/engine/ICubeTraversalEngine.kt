package com.duel2048.app.cube.magic.presentation.cube.engine

import com.duel2048.app.cube.magic.domain.CubeSettings
import com.duel2048.app.cube.magic.grafic.ICubeGameEngine
import com.duel2048.app.cube.magic.presentation.cube.CubeDrawCommand

interface ICubeTraversalEngine {
    fun buildFrame(
        engine: ICubeGameEngine,
        settings: CubeSettings,
        rotationState: CubeRotationState,
        projectionMatrix: FloatArray
    ): List<CubeDrawCommand>
}
