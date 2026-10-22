package com.duel2048.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.duel2048.app.MainViewModel
import com.duel2048.app.cube.CubeDuelUiState
import com.duel2048.app.cube.model3d.CubeModelBridge
import com.duel2048.app.cube.model3d.RubiksCubeView
import com.duel2048.app.game.DuelPhase
import com.duel2048.shared.cube.CubeMove

/** Cube duel on the glb model renderer (the only cube engine). */
@Composable
internal fun CubeDuelModel3dRoute(vm: MainViewModel, cube: CubeDuelUiState, onLeave: () -> Unit = { vm.leaveCubeDuel() }) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var mine by remember(cube.scrambleNonce) { mutableStateOf<RubiksCubeView?>(null) }
    var opp by remember(cube.scrambleNonce) { mutableStateOf<RubiksCubeView?>(null) }

    var renderedOpponent by remember(cube.scrambleNonce, cube.positionVersion, opp) { mutableStateOf(0) }
    LaunchedEffect(cube.scrambleNonce, cube.positionVersion, mine) {
        mine?.let {
            if (cube.played.isNotEmpty() || cube.positionVersion > 0) it.restorePosition(cube.scramble + cube.played)
            else CubeModelBridge.applyScrambleAnimated(it, cube.scramble)
        }
    }
    LaunchedEffect(cube.scrambleNonce, cube.positionVersion, opp) {
        opp?.let {
            if (cube.opponentPlayed.isNotEmpty() || cube.positionVersion > 0) it.restorePosition(cube.scramble + cube.opponentPlayed)
            else CubeModelBridge.applyScrambleAnimated(it, cube.scramble)
            renderedOpponent = cube.opponentPlayed.size
        }
    }
    LaunchedEffect(cube.opponentPlayed, opp) {
        val view = opp ?: return@LaunchedEffect
        for (notation in cube.opponentPlayed.drop(renderedOpponent)) {
            CubeMove.parse(notation)?.let { CubeModelBridge.applyMoveAnimated(view, it) }
        }
        renderedOpponent = cube.opponentPlayed.size
    }

    DisposableEffect(lifecycleOwner, mine, opp) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { mine?.onResume(); opp?.onResume() }
                Lifecycle.Event.ON_PAUSE -> { mine?.onPause(); opp?.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mine?.onPause()
            opp?.onPause()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CubeDuelOverlay(
        cube = cube,
        onLeave = onLeave,
        onGiveUp = { vm.revealCubeSolver() },
        onHint = { vm.cubeHint() },
        opponentView = {
            AndroidView(
                factory = { ctx ->
                    RubiksCubeView(ctx).also { view ->
                        CubeModelBridge.configure(view, gesturesEnabled = false)
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
                    RubiksCubeView(ctx).also { view ->
                        CubeModelBridge.configure(view, gesturesEnabled = false)
                        mine = view
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (mine == null) mine = view
                    val canTurn = cube.phase == DuelPhase.PLAYING && !cube.solved && !cube.reconnecting
                    view.gesturesEnabled = canTurn
                    view.onMove = { move ->
                        if (canTurn && !view.animator.isBusy()) {
                            vm.applyCubeMove(move)
                            CubeModelBridge.applyMoveAnimated(view, move)
                        }
                    }
                },
            )
        },
    )
}
