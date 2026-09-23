package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.theme.LocalPalette

@Composable
fun CubeResultScreen(vm: MainViewModel) {
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val result = cube.result
    BackHandler { vm.goHome() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            when {
                cube.won -> stringResource(R.string.cube_result_win)
                cube.draw -> stringResource(R.string.cube_result_draw)
                else -> stringResource(R.string.cube_result_loss)
            },
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = when {
                cube.won -> palette.success
                cube.draw -> palette.gold
                else -> palette.danger
            },
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            result?.results?.forEach { r ->
                val name = if (r.playerId == cube.myId) stringResource(R.string.you) else cube.opponent?.name ?: "?"
                Text(
                    "$name · ${r.moves} moves · ${if (r.solved) "solved" else "—"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        NeonButton(stringResource(R.string.play_again), modifier = Modifier.fillMaxWidth()) { vm.playCubeAgain() }
        Spacer(Modifier.height(10.dp))
        NeonButton(stringResource(R.string.home), modifier = Modifier.fillMaxWidth(), colors = listOf(palette.accent2, palette.accent)) { vm.goHome() }
    }
}
