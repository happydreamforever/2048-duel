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
    var mine by remember(cube.scrambleNonce) { mutableStateOf<AnimCube?>(null) }
    var opp by remember(cube.scrambleNonce) { mutableStateOf<AnimCube?>(null) }

    LaunchedEffect(cube.scrambleNonce, cube.scramble, mine, opp) {
        val scramble = cube.scramble
        if (scramble.isEmpty()) return@LaunchedEffect
        mine?.let { Cube2Bridge.applyScrambleAnimated(it, scramble) }
        opp?.let { Cube2Bridge.applyScrambleAnimated(it, scramble) }
    }
    LaunchedEffect(cube.oppSerial, opp) {
        val view = opp ?: return@LaunchedEffect
        val move = CubeMove.parse(cube.oppLastMove) ?: return@LaunchedEffect
        Cube2Bridge.applyMoveAnimated(view, move)
    }

    CubeDuelOverlay(
        cube = cube,
        onMove = { move ->
            mine?.let { Cube2Bridge.applyMoveAnimated(it, move) }
            vm.applyCubeMove(move)
        },
        onLeave = { vm.leaveCubeDuel() },
        onGiveUp = { vm.revealCubeSolver() },
        onHint = { vm.cubeHint() },
        opponentView = {
            AndroidView(
                factory = { ctx ->
                    AnimCube(ctx).also { view ->
                        Cube2Bridge.configure(view)
                        opp = view
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> if (opp == null) opp = view },
            )
        },
        playerView = {
            AndroidView(
                factory = { ctx ->
                    AnimCube(ctx).also { view ->
                        Cube2Bridge.configure(view)
                        mine = view
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> if (mine == null) mine = view },
            )
        },
    )
}
