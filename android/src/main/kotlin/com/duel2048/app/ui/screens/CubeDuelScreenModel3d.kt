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
import com.duel2048.shared.cube.CubeMove

/** Cube duel on the glb model renderer (the only cube engine). */
@Composable
internal fun CubeDuelModel3dRoute(vm: MainViewModel, cube: CubeDuelUiState) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var mine by remember(cube.scrambleNonce) { mutableStateOf<RubiksCubeView?>(null) }
    var opp by remember(cube.scrambleNonce) { mutableStateOf<RubiksCubeView?>(null) }

    LaunchedEffect(cube.scrambleNonce, cube.scramble, mine, opp) {
        val scramble = cube.scramble
        if (scramble.isEmpty()) return@LaunchedEffect
        mine?.let { CubeModelBridge.applyScrambleAnimated(it, scramble) }
        opp?.let { CubeModelBridge.applyScrambleAnimated(it, scramble) }
    }
    LaunchedEffect(cube.oppSerial, opp) {
        val view = opp ?: return@LaunchedEffect
        val move = CubeMove.parse(cube.oppLastMove) ?: return@LaunchedEffect
        CubeModelBridge.applyMoveAnimated(view, move)
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
        onLeave = { vm.leaveCubeDuel() },
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
                        CubeModelBridge.configure(view, gesturesEnabled = true)
                        // Face drags behave exactly like button presses: animate + report.
                        view.onMove = { move ->
                            CubeModelBridge.applyMoveAnimated(view, move)
                            vm.applyCubeMove(move)
                        }
                        mine = view
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> if (mine == null) mine = view },
            )
        },
    )
}
