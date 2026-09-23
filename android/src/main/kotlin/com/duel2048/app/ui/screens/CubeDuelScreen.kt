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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.cube.CubeDuelUiState
import com.duel2048.app.data.CubeRenderer as CubeRendererChoice
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.cube.CubeMove

@Composable
fun CubeDuelScreen(vm: MainViewModel) {
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val renderer = CubeRendererChoice.fromId(settings.cubeRenderer)
    BackHandler { vm.leaveCubeDuel() }

    when (renderer) {
        CubeRendererChoice.CUBE2 -> CubeDuelCube2Route(vm, cube)
        CubeRendererChoice.MAGIC -> CubeDuelMagicRoute(vm, cube)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CubeDuelOverlay(
    cube: CubeDuelUiState,
    onMove: (CubeMove) -> Unit,
    onLeave: () -> Unit,
    cubeView: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        cubeView()

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (cube.training) R.string.cube_training_title else R.string.cube_duel_title),
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
                        moveButtons(onMove)
                    }
                } else if (cube.solved) {
                    Text(stringResource(R.string.cube_solved_wait), color = palette.success, modifier = Modifier.padding(8.dp))
                }
            }
            TextButton(onClick = onLeave, modifier = Modifier.align(Alignment.End)) {
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
