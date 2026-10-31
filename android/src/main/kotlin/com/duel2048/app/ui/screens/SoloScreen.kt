package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.fx.LocalGameFx
import com.duel2048.app.ui.components.BoardFx
import com.duel2048.app.ui.components.BoardView
import com.duel2048.app.ui.components.handleEffect
import com.duel2048.app.ui.input.keyboardInput
import com.duel2048.app.ui.landscape.*
import com.duel2048.app.ui.rememberFxStrings
import com.duel2048.app.ui.theme.LocalPalette

@Composable
fun SoloScreen(vm: MainViewModel) {
    val solo by vm.solo.state.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val gfx = LocalGameFx.current
    val scope = rememberCoroutineScope()
    val fx = remember { BoardFx(scope) }
    val fxStrings = rememberFxStrings()
    LaunchedEffect(Unit) {
        vm.solo.effects.collect { handleEffect(it, fx, null, gfx, palette, fxStrings) }
    }
    BackHandler { vm.goHome() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    var restart by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxSize().padding(12.dp).focusRequester(focus).focusable().keyboardInput {
            vm.solo.swipe(it)
        },
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BoxWithConstraints(
                Modifier.weight(.6f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    BoardView(
                        solo.board,
                        Modifier.size(side),
                        fx = fx,
                        dimmed = solo.gameOver,
                        onSwipe = { vm.solo.swipe(it) })
                }
            Column(
                Modifier.weight(.4f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "2048 · Solo",
                        color = palette.accent,
                        style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Score", solo.score.toString(), Modifier.weight(1f))
                        Metric("Best", solo.best.toString(), Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.solo_zen), color = palette.textSecondary)
                    if (solo.gameOver)
                        Text(stringResource(R.string.game_over), color = palette.gold)
                    ArcadeAction(stringResource(R.string.new_game)) { restart = true }
                    ArcadeAction(stringResource(R.string.home)) { vm.goHome() }
                    Text(
                        stringResource(R.string.swipe_moves, solo.moves),
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
        }
    if (restart)
        AlertDialog(
            onDismissRequest = { restart = false },
            title = { Text("Start a new game?") },
            text = { Text("This replaces your current practice board.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        restart = false
                        vm.solo.newGame()
                    }) {
                        Text("New Game")
                    }
            },
            dismissButton = { TextButton(onClick = { restart = false }) { Text("Cancel") } })
}
