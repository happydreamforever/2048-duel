package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.cube.CubeDependencies
import com.duel2048.app.cube.CubeWcaMapping
import com.duel2048.app.cube.magic.grafic.CubeRenderer
import com.duel2048.app.cube.magic.grafic.CubeSurfaceView
import com.duel2048.app.cube.magic.presentation.cube.CubeViewModel
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.cube.CubeMove
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CubeDuelScreen(vm: MainViewModel) {
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val cubeVm: CubeViewModel = viewModel(
        key = cube.scrambleNonce.toString(),
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CubeDependencies.createCubeViewModel() as T
        },
    )
    val palette = LocalPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler { vm.leaveCubeDuel() }

    val surfaceView = remember(cubeVm) {
        CubeSurfaceView(context, cubeVm).apply {
            setEGLContextClientVersion(3)
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

    LaunchedEffect(cube.scrambleNonce, cube.scramble) {
        if (cube.scramble.isEmpty()) return@LaunchedEffect
        delay(300)
        val moves = cube.scramble.mapNotNull { CubeMove.parse(it) }
        for (move in moves) {
            CubeWcaMapping.applyToEngine(cubeVm.engine, move)
            delay(40)
        }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        AndroidView(factory = { surfaceView }, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.cube_duel_title),
                style = MaterialTheme.typography.titleMedium,
                color = palette.accent,
                fontWeight = FontWeight.Black,
            )
            if (cube.countdown != null) {
                Text("${cube.countdown}", fontSize = 48.sp, fontWeight = FontWeight.Black, color = palette.gold)
            } else if (cube.phase == DuelPhase.PLAYING) {
                val rem = cube.remainingNow(System.currentTimeMillis()) / 1000
                Text(stringResource(R.string.cube_timer, rem), color = palette.textSecondary)
            }
            Text(
                stringResource(R.string.cube_moves_line, cube.myMoves, cube.oppMoves),
                color = palette.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            cube.opponent?.let {
                Text(stringResource(R.string.cube_vs, it.name), color = palette.textSecondary)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                if (cube.phase == DuelPhase.PLAYING && !cube.solved) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        moveButtons { move ->
                            CubeWcaMapping.applyToEngine(cubeVm.engine, move)
                            vm.applyCubeMove(move)
                        }
                    }
                } else if (cube.solved) {
                    Text(stringResource(R.string.cube_solved_wait), color = palette.success, modifier = Modifier.padding(8.dp))
                }
            }
            TextButton(onClick = { vm.leaveCubeDuel() }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.leave), color = palette.danger)
            }
        }
    }
}

@Composable
private fun moveButtons(onMove: (CubeMove) -> Unit) {
    val faces = listOf(
        CubeMove.U, CubeMove.Ui,
        CubeMove.R, CubeMove.Ri,
        CubeMove.F, CubeMove.Fi,
        CubeMove.D, CubeMove.Di,
        CubeMove.L, CubeMove.Li,
        CubeMove.B, CubeMove.Bi,
    )
    for (move in faces) {
        TextButton(onClick = { onMove(move) }) {
            Text(move.notation(), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
