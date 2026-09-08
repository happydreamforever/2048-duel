package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.rememberFxStrings
import com.duel2048.app.fx.LocalGameFx
import com.duel2048.app.ui.components.BoardFx
import com.duel2048.app.ui.components.BoardView
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.components.ScoreCounter
import com.duel2048.app.ui.components.StatPill
import com.duel2048.app.ui.components.handleEffect
import com.duel2048.app.ui.input.keyboardInput
import com.duel2048.app.ui.theme.LocalPalette

@Composable
fun SoloScreen(vm: MainViewModel) {
    val solo by vm.solo.state.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val gfx = LocalGameFx.current
    val scope = rememberCoroutineScope()
    val fx = remember { BoardFx(scope) }
    val fxStrings = rememberFxStrings()
    LaunchedEffect(Unit) { vm.solo.effects.collect { handleEffect(it, fx, null, gfx, palette, fxStrings) } }
    BackHandler { vm.goHome() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
            .focusRequester(focus)
            .focusable()
            .keyboardInput { vm.solo.swipe(it) },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.goHome() }) { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home), tint = palette.textSecondary) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.score_caps), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                ScoreCounter(solo.score)
            }
            Spacer(Modifier.width(18.dp))
            StatPill(stringResource(R.string.best), solo.best.toString(), valueColor = palette.gold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.solo.newGame() }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.new_game), tint = palette.textSecondary) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.solo_zen), style = MaterialTheme.typography.labelLarge, color = palette.accent, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            BoardView(solo.board, fx = fx, dimmed = solo.gameOver, onSwipe = { vm.solo.swipe(it) })
            if (solo.gameOver) {
                GlassCard(Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text(stringResource(R.string.game_over), fontWeight = FontWeight.Black, fontSize = 26.sp, color = palette.danger, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(stringResource(R.string.score_and_tile, solo.score, solo.maxTile), color = palette.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(14.dp))
                    NeonButton(stringResource(R.string.new_game), modifier = Modifier.fillMaxWidth()) { vm.solo.newGame() }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.swipe_moves, solo.moves), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}
