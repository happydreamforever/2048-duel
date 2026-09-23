package com.duel2048.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.catalinjurjiu.animcubeandroid.AnimCube
import com.duel2048.app.MainViewModel
import com.duel2048.app.cube.CubeDuelUiState
import com.duel2048.app.cube2.Cube2Bridge
import com.duel2048.shared.cube.CubeMove

@Composable
internal fun CubeDuelCube2Route(vm: MainViewModel, cube: CubeDuelUiState) {
    var animCube by remember(cube.scrambleNonce) { mutableStateOf<AnimCube?>(null) }

    LaunchedEffect(cube.scrambleNonce, cube.scramble, animCube) {
        val view = animCube ?: return@LaunchedEffect
        if (cube.scramble.isEmpty()) return@LaunchedEffect
        Cube2Bridge.applyScrambleAnimated(view, cube.scramble)
    }

    CubeDuelOverlay(
        cube = cube,
        onMove = { move ->
            animCube?.let { Cube2Bridge.applyMoveAnimated(it, move) }
            vm.applyCubeMove(move)
        },
        onLeave = { vm.leaveCubeDuel() },
    ) {
        AndroidView(
            factory = { ctx ->
                AnimCube(ctx).also { view ->
                    Cube2Bridge.configure(view)
                    animCube = view
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> if (animCube == null) animCube = view },
        )
    }
}
