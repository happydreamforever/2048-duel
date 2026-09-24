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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catalinjurjiu.animcubeandroid.AnimCube
import com.duel2048.app.MainViewModel
import com.duel2048.app.cube.CubeDependencies
import com.duel2048.app.cube.CubeDuelUiState
import com.duel2048.app.cube.magic.grafic.CubeRenderer
import com.duel2048.app.cube.magic.grafic.CubeSurfaceView
import com.duel2048.app.cube.magic.presentation.cube.CubeViewModel
import com.duel2048.app.cube2.Cube2Bridge
import com.duel2048.shared.cube.CubeMove
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
internal fun CubeDuelMagicRoute(vm: MainViewModel, cube: CubeDuelUiState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cubeVm: CubeViewModel = viewModel(
        key = cube.scrambleNonce.toString(),
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CubeDependencies.createCubeViewModel() as T
        },
    )

    val surfaceView = remember(cubeVm) {
        CubeSurfaceView(context, cubeVm).apply {
            setRenderer(CubeRenderer(cubeVm))
        }
    }

    DisposableEffect(lifecycleOwner, surfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> surfaceView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            surfaceView.onPause()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var opp by remember(cube.scrambleNonce) { mutableStateOf<AnimCube?>(null) }

    LaunchedEffect(cube.scrambleNonce, cube.scramble, surfaceView) {
        if (cube.scramble.isEmpty()) return@LaunchedEffect
        cubeVm.settingsState.filter { it != null }.first()
        val moves = cube.scramble.mapNotNull { CubeMove.parse(it) }
        surfaceView.applyScramble(cubeVm, moves)
        opp?.let { Cube2Bridge.applyScrambleAnimated(it, cube.scramble) }
    }
    LaunchedEffect(cube.oppSerial, opp) {
        val view = opp ?: return@LaunchedEffect
        val move = CubeMove.parse(cube.oppLastMove) ?: return@LaunchedEffect
        Cube2Bridge.applyMoveAnimated(view, move)
    }

    CubeDuelOverlay(
        cube = cube,
        onMove = { move ->
            surfaceView.applyMove(cubeVm, move)
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
            AndroidView(factory = { surfaceView }, modifier = Modifier.fillMaxSize())
        },
    )
}
