package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.components.ParticleLayer
import com.duel2048.app.ui.components.ParticleSystem
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.protocol.EndReason
import com.duel2048.shared.protocol.PlayerResult
import kotlinx.coroutines.delay

@Composable
fun ResultScreen(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val result = duel.result
    val won = duel.won
    val draw = duel.draw
    BackHandler { vm.goHome() }

    val confetti = remember { ParticleSystem(3) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    if (won) {
        LaunchedEffect(Unit) {
            val w = with(density) { config.screenWidthDp.dp.toPx() }
            val h = with(density) { config.screenHeightDp.dp.toPx() }
            repeat(4) {
                confetti.confetti(w, h, listOf(palette.accent, palette.accent2, palette.gold, Color.White, palette.success))
                delay(650)
            }
        }
    }

    val me = result?.results?.firstOrNull { it.playerId == duel.myId }
    val opp = result?.results?.firstOrNull { it.playerId != duel.myId }
    var showStats by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(350); showStats = true }

    Box(Modifier.fillMaxSize()) {
        if (won) ParticleLayer(confetti, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val (title, color) = when {
                draw -> stringResource(R.string.draw) to palette.gold
                won -> stringResource(R.string.victory) to palette.success
                else -> stringResource(R.string.defeat) to palette.danger
            }
            val scale = remember { Animatable(0.3f) }
            LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
            Text(
                title,
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                color = color,
                style = TextStyle(shadow = Shadow(color.copy(alpha = 0.9f), Offset.Zero, 40f)),
            )
            Text(
                when (result?.reason) {
                    EndReason.BOARD_FULL -> stringResource(if (won) R.string.reason_board_full_won else if (draw) R.string.reason_board_full_draw else R.string.reason_board_full_lost)
                    EndReason.TIME_UP -> stringResource(R.string.reason_time_up)
                    EndReason.FORFEIT -> stringResource(if (won) R.string.reason_forfeit_won else R.string.reason_forfeit_lost)
                    EndReason.SOLVED, EndReason.FORFEIT_CUBE -> ""
                    null -> ""
                },
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            AnimatedVisibility(visible = showStats, enter = fadeIn() + slideInVertically { it / 3 }) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("", Modifier.weight(1.2f))
                        Text(stringResource(R.string.you).uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = palette.accent, textAlign = TextAlign.End)
                        Text((duel.opponent.info?.name ?: stringResource(R.string.opponent)).uppercase().take(8), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = palette.danger, textAlign = TextAlign.End, maxLines = 1)
                    }
                    Divider(Modifier.padding(vertical = 8.dp), color = palette.surfaceBorder)
                    ResultRow(stringResource(R.string.score), me, opp) { it.score }
                    ResultRow(stringResource(R.string.max_tile), me, opp) { it.maxTile }
                    ResultRow(stringResource(R.string.moves), me, opp) { it.moves }
                    ResultRow(stringResource(R.string.merges), me, opp) { it.merges }
                    ResultRow(stringResource(R.string.best_combo), me, opp) { it.bestCombo }
                    ResultRow(stringResource(R.string.garbage_sent), me, opp) { it.garbageSent }
                    ResultRow(stringResource(R.string.garbage_received), me, opp, lowerIsBetter = true) { it.garbageReceived }
                }
            }
            Spacer(Modifier.height(26.dp))
            NeonButton(stringResource(R.string.play_again), modifier = Modifier.fillMaxWidth()) { vm.playAgain() }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.goHome() }) { Text(stringResource(R.string.home), color = palette.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
        }
    }
}

@Composable
private fun ResultRow(label: String, me: PlayerResult?, opp: PlayerResult?, lowerIsBetter: Boolean = false, selector: (PlayerResult) -> Int) {
    val palette = LocalPalette.current
    val a = me?.let(selector)
    val b = opp?.let(selector)
    fun better(x: Int?, y: Int?): Boolean = x != null && y != null && x != y && ((x > y) != lowerIsBetter)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1.2f), color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(a?.toString() ?: "–", Modifier.weight(1f), color = if (better(a, b)) palette.success else palette.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        Text(b?.toString() ?: "–", Modifier.weight(1f), color = if (better(b, a)) palette.success else palette.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}
