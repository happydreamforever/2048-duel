@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.cube.CubeDuelUiState
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.ui.landscape.*
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.cube.CubeMove
import kotlinx.coroutines.delay

@Composable
fun CubeDuelScreen(vm: MainViewModel) {
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (cube.training || cube.phase == DuelPhase.OVER) vm.leaveCubeDuel()
        else confirmLeave = true
    }
    BackHandler(onBack = leave)
    CubeDuelModel3dRoute(vm, cube, leave)
    if (confirmLeave)
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.product_leave_match)) },
            text = { Text(stringResource(R.string.product_leave_match_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        vm.leaveCubeDuel()
                    }) {
                        Text(stringResource(R.string.leave))
                    }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.stay))
                }
            })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CubeDuelOverlay(
    cube: CubeDuelUiState,
    onLeave: () -> Unit,
    onGiveUp: () -> Unit,
    onHint: () -> Unit = {},
    onTurn: (CubeMove) -> Unit = {},
    onResetView: () -> Unit = {},
    opponentView: @Composable () -> Unit,
    playerView: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    var inverse by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cube.phase) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val enabled = cube.phase == DuelPhase.PLAYING && !cube.solved && !cube.reconnecting
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (cube.me?.name ?: "You") + "  vs  " + (cube.opponent?.name ?: "Trainer"),
                Modifier.weight(1f),
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleMedium)
            Text(
                if (cube.reconnecting) "Reconnecting…"
                else
                    cube.countdown?.toString()
                        ?: com.duel2048.app.ui.components.formatClock(cube.remainingNow(now)),
                color = palette.gold)
            TextButton(onClick = onLeave) {
                Text(stringResource(R.string.leave), color = palette.danger)
            }
        }
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(.52f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { playerView() }
                    Text(
                        "Drag a layer to turn · Drag background to orbit",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary)
                }
                Column(Modifier.weight(.2f).fillMaxHeight()) {
                    Text(
                        "Opponent",
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.weight(1f).fillMaxWidth()) { opponentView() }
                    Text(
                        "${cube.myMoves} / ${cube.oppMoves} moves",
                        color = palette.accent,
                        style = MaterialTheme.typography.bodySmall)
                    if (cube.oppSolved) Text("Opponent solved", color = palette.gold)
                }
                Column(
                    Modifier.weight(.28f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Face turns",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.labelSmall)
                        listOf(listOf("U", "D", "L"), listOf("R", "F", "B"), listOf("M", "E", "S"))
                            .forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    row.forEach { face ->
                                        ArcadeAction(
                                            face + (if (inverse) "′" else ""),
                                            Modifier.weight(1f),
                                            enabled = enabled) {
                                                CubeMove.parse(face + if (inverse) "'" else "")
                                                    ?.let(onTurn)
                                            }
                                    }
                                }
                            }
                        androidx.compose.material3.FilterChip(
                            inverse,
                            { inverse = !inverse },
                            label = { Text("Inverse  ′") },
                            modifier = Modifier.heightIn(min = 48.dp))
                        TextButton(onClick = onResetView) { Text("Reset View") }
                        if (enabled)
                            TextButton(onClick = onGiveUp) {
                                Text(
                                    stringResource(R.string.give_up_solver),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.gold)
                            }
                        if (cube.guided && enabled)
                            TextButton(onClick = onHint) { Text(stringResource(R.string.hint)) }
                        if (cube.solved)
                            Text(stringResource(R.string.cube_solved_wait), color = palette.success)
                        if (cube.solutionHint.isNotBlank())
                            Text(
                                cube.solutionHint,
                                color = palette.gold,
                                style = MaterialTheme.typography.bodySmall)
                    }
            }
    }
}
