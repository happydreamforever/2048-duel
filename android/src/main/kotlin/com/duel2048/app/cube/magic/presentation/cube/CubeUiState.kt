package com.duel2048.app.cube.magic.presentation.cube

/**
 * Single source of truth for the cube screen's observable state.
 *
 * [drawCommands] is consumed by [com.duel2048.app.cube.magic.grafic.CubeRenderer] on the GL
 * thread every frame. [isDraggingSlice] is available for any UI overlays that need to react
 * to an active face drag.
 */
data class CubeUiState(
    val drawCommands: List<CubeDrawCommand> = emptyList(),
    val isDraggingSlice: Boolean = false,
)
