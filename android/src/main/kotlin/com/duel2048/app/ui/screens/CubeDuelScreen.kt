package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.cube.CubeDuelUiState
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.theme.LocalPalette

@Composable
fun CubeDuelScreen(vm: MainViewModel) {
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    BackHandler { vm.leaveCubeDuel() }
    CubeDuelModel3dRoute(vm, cube)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CubeDuelOverlay(
    cube: CubeDuelUiState,
    onLeave: () -> Unit,
    onGiveUp: () -> Unit,
    onHint: () -> Unit = {},
    opponentView: @Composable () -> Unit,
    playerView: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(8.dp)) {
        Text(
            stringResource(if (cube.training) R.string.cube_training_title else R.string.cube_duel_title),
            style = MaterialTheme.typography.titleMedium,
            color = palette.accent,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (cube.countdown != null) {
            Text("${cube.countdown}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = palette.gold, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (cube.phase == DuelPhase.PLAYING) {
            val rem = cube.remainingNow(System.currentTimeMillis()) / 1000
            Text(stringResource(R.string.cube_timer, rem), color = palette.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Text(
            stringResource(R.string.cube_moves_line, cube.myMoves, cube.oppMoves),
            color = palette.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(stringResource(R.string.opponent_cube), color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
        Box(Modifier.fillMaxWidth().weight(1f)) { opponentView() }
        Text(stringResource(R.string.your_cube), color = palette.accent, style = MaterialTheme.typography.labelSmall)
        Box(Modifier.fillMaxWidth().weight(1f)) { playerView() }
        if (cube.solutionHint.isNotBlank()) {
            Text(stringResource(R.string.solver_line, cube.solutionHint), color = palette.gold, style = MaterialTheme.typography.bodySmall)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            if (cube.phase == DuelPhase.PLAYING && !cube.solved) {
                TextButton(onClick = onGiveUp) { Text(stringResource(R.string.give_up_solver), color = palette.gold) }
                if (cube.guided) {
                    TextButton(onClick = onHint) { Text(stringResource(R.string.hint), color = palette.accent) }
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
