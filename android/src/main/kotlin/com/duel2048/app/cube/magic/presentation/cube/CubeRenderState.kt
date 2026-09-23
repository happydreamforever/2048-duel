package com.duel2048.app.cube.magic.presentation.cube

import com.duel2048.app.cube.magic.grafic.Cube

data class CubeRenderState(
    val drawCommands: List<CubeDrawCommand> = emptyList()
)

data class CubeDrawCommand(
    val cube: Cube,
    val mvpMatrix: FloatArray
)
