package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.ui.components.Avatar
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.protocol.MatchMode

@Composable
fun MatchmakingScreen(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { now = System.currentTimeMillis() }
    }
    BackHandler { vm.cancelSearch() }

    val elapsedMs = (now - duel.searchStartedAt).coerceAtLeast(0)
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Radar(Modifier.size(230.dp))
                Avatar(settings.accountName.ifBlank { settings.playerName.ifBlank { "?" } }, 64.dp)
            }
            Spacer(Modifier.height(36.dp))
            Text(
                stringResource(
                    when {
                        duel.phase == DuelPhase.CONNECTING -> R.string.connecting
                        duel.mode == MatchMode.BOT -> R.string.summoning_bot
                        else -> R.string.searching_opponent
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            if (duel.phase == DuelPhase.SEARCHING && duel.mode == MatchMode.PVP && duel.botFallbackMs > 0) {
                val left = ((duel.botFallbackMs - elapsedMs) / 1000).coerceAtLeast(0)
                Text(
                    stringResource(R.string.bot_fallback_in, left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = (elapsedMs.toFloat() / duel.botFallbackMs).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(0.7f),
                    color = palette.accent,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.online_elapsed, duel.onlinePlayers, elapsedMs / 1000), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Spacer(Modifier.height(40.dp))
            NeonButton(stringResource(R.string.cancel), colors = listOf(Color(0xFF3B4A66), Color(0xFF1F2A44))) { vm.cancelSearch() }
        }
    }
}

@Composable
private fun Radar(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val t by rememberInfiniteTransition(label = "radar").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        val maxR = size.minDimension / 2f
        for (i in 0 until 3) {
            val p = (t + i / 3f) % 1f
            val r = maxR * (0.25f + 0.75f * p)
            drawCircle(palette.accent.copy(alpha = (1f - p) * 0.5f), radius = r, style = Stroke(2.dp.toPx()))
        }
        drawCircle(palette.accent2.copy(alpha = 0.12f), radius = maxR * 0.3f)
    }
}
