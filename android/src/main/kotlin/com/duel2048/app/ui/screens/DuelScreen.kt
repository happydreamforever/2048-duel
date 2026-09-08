package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.rememberFxStrings
import com.duel2048.app.fx.LocalGameFx
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.game.DuelUiState
import com.duel2048.app.ui.components.AttackMeter
import com.duel2048.app.ui.components.Avatar
import com.duel2048.app.ui.components.BoardFx
import com.duel2048.app.ui.components.BoardView
import com.duel2048.app.ui.components.ScoreCounter
import com.duel2048.app.ui.components.StatRow
import com.duel2048.app.ui.components.formatClock
import com.duel2048.app.ui.components.handleEffect
import com.duel2048.app.ui.input.keyboardInput
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.engine.Rules
import kotlinx.coroutines.delay

@Composable
fun DuelScreen(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val fxStrings = rememberFxStrings()
    val palette = LocalPalette.current
    val gfx = LocalGameFx.current
    val scope = rememberCoroutineScope()
    val myFx = remember { BoardFx(scope) }
    val oppFx = remember { BoardFx(scope) }

    LaunchedEffect(session) {
        session.effects.collect { handleEffect(it, myFx, oppFx, gfx, palette, fxStrings) }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(duel.phase) {
        if (duel.phase == DuelPhase.PLAYING) {
            while (true) withFrameMillis { now = System.currentTimeMillis() }
        }
    }
    var showGo by remember { mutableStateOf(false) }
    LaunchedEffect(duel.phase) {
        if (duel.phase == DuelPhase.PLAYING) {
            showGo = true
            delay(900)
            showGo = false
        }
    }
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler {
        if (duel.phase == DuelPhase.OVER) vm.goHome() else confirmLeave = true
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val remaining = duel.remainingNow(now)
    val playing = duel.phase == DuelPhase.PLAYING

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focus)
            .focusable()
            .keyboardInput { session.swipe(it) },
    ) {
        VsHeader(duel, remaining)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(0.42f)) {
                Text(
                    (duel.opponent.info?.name ?: stringResource(R.string.opponent)).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                BoardView(duel.opponent.board, Modifier.fillMaxWidth(), fx = oppFx, dimmed = duel.opponent.gameOver)
                Spacer(Modifier.height(6.dp))
                AttackMeter(duel.opponent.energy, compact = true)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(0.58f)) {
                TimerBar(remaining, duel.durationMs, playing)
                Spacer(Modifier.height(8.dp))
                StatRow(stringResource(R.string.sent), duel.me.garbageSent.toString(), palette.danger)
                StatRow(stringResource(R.string.received), duel.me.garbageReceived.toString())
                StatRow(stringResource(R.string.max_tile), duel.me.maxTile.toString(), palette.gold)
                if (duel.training) {
                    StatRow(stringResource(R.string.training_title), stringResource(R.string.offline), palette.accent)
                } else {
                    StatRow(stringResource(R.string.ping), duel.pingMs?.let { "$it ms" } ?: "–")
                }
                AnimatedVisibility(visible = duel.incomingWarning > 0, enter = fadeIn(), exit = fadeOut()) {
                    IncomingBadge(duel.incomingWarning)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AttackMeter(duel.me.energy, Modifier.fillMaxWidth(), label = stringResource(R.string.attack_meter_label, Rules.ATTACK_COST))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            BoardView(
                duel.me.board,
                fx = myFx,
                dimmed = duel.me.gameOver && duel.phase != DuelPhase.OVER,
                onSwipe = { if (playing) session.swipe(it) },
            )
            CountdownOverlay(duel.countdown, showGo)
            if (duel.phase == DuelPhase.OVER) ResultFlash(duel)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (playing) R.string.hint_playing else R.string.get_ready),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { if (duel.phase == DuelPhase.OVER) vm.goHome() else confirmLeave = true }) {
                Text(stringResource(if (duel.phase == DuelPhase.OVER) R.string.continue_label else R.string.leave), color = palette.danger, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = palette.bgTop,
            title = { Text(stringResource(R.string.leave_title), color = palette.textPrimary) },
            text = { Text(stringResource(R.string.leave_text), color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; vm.leaveMatch() }) { Text(stringResource(R.string.leave), color = palette.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.stay), color = palette.accent) } },
        )
    }
}

@Composable
private fun VsHeader(duel: DuelUiState, remaining: Long) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val youLabel = stringResource(R.string.you)
        val oppLabel = stringResource(R.string.opponent)
        Avatar(duel.me.info?.name ?: youLabel, 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text((duel.me.info?.name ?: youLabel).uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.accent, maxLines = 1)
            ScoreCounter(duel.me.score)
        }
        TimerRing(remaining, duel.durationMs, duel.phase == DuelPhase.PLAYING)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text((duel.opponent.info?.name ?: oppLabel).uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.danger, maxLines = 1)
            ScoreCounter(duel.opponent.score)
        }
        Spacer(Modifier.width(10.dp))
        Avatar(duel.opponent.info?.name ?: oppLabel, 44.dp, isBot = duel.opponent.info?.isBot == true)
    }
}

@Composable
private fun TimerRing(remaining: Long, duration: Long, playing: Boolean) {
    val palette = LocalPalette.current
    val fraction = if (duration > 0) (remaining.toFloat() / duration).coerceIn(0f, 1f) else 1f
    val urgent = playing && remaining in 1..15_000
    val pulse by rememberInfiniteTransition(label = "urgent").animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "u")
    val color = if (urgent) palette.danger else palette.accent
    Box(Modifier.size(64.dp).graphicsLayer { if (urgent) { scaleX = pulse; scaleY = pulse } }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.1f), style = Stroke(stroke))
            drawArc(color, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawArc(color.copy(alpha = 0.3f), startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, style = Stroke(stroke * 2.2f))
        }
        Text(formatClock(remaining), fontWeight = FontWeight.Black, fontSize = 14.sp, color = palette.textPrimary)
    }
}

@Composable
private fun TimerBar(remaining: Long, duration: Long, playing: Boolean) {
    val palette = LocalPalette.current
    val fraction = if (duration > 0) (remaining.toFloat() / duration).coerceIn(0f, 1f) else 1f
    val urgent = playing && remaining in 1..15_000
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.time), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text(formatClock(remaining), style = MaterialTheme.typography.labelSmall, color = if (urgent) palette.danger else palette.textPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.1f), cornerRadius = r)
            drawRoundRect(if (urgent) palette.danger else palette.accent2, size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height), cornerRadius = r)
        }
    }
}

@Composable
private fun IncomingBadge(count: Int) {
    val palette = LocalPalette.current
    val pulse by rememberInfiniteTransition(label = "inc").animateFloat(0.6f, 1f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "a")
    Box(
        Modifier
            .padding(top = 6.dp)
            .background(palette.danger.copy(alpha = 0.18f * pulse + 0.1f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(stringResource(R.string.incoming, count), color = palette.danger, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
private fun CountdownOverlay(countdown: Int?, showGo: Boolean) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (countdown != null) {
            key(countdown) {
                PopText(countdown.toString(), palette.accent, 110.sp)
            }
        } else if (showGo) {
            PopText(stringResource(R.string.go), palette.success, 96.sp)
        }
    }
}

@Composable
private fun PopText(text: String, color: Color, size: androidx.compose.ui.unit.TextUnit) {
    val scale = remember { Animatable(2.2f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.snapTo(1f)
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        alpha.animateTo(0f, tween(450, delayMillis = 150))
    }
    Text(
        text,
        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value },
        fontSize = size,
        fontWeight = FontWeight.Black,
        color = color,
        style = TextStyle(shadow = Shadow(color.copy(alpha = 0.8f), Offset.Zero, 40f)),
    )
}

@Composable
private fun ResultFlash(duel: DuelUiState) {
    val palette = LocalPalette.current
    val (title, color) = when {
        duel.draw -> stringResource(R.string.draw) to palette.gold
        duel.won -> stringResource(R.string.victory) to palette.success
        else -> stringResource(R.string.defeat) to palette.danger
    }
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
        Text(
            title,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            fontSize = 54.sp,
            fontWeight = FontWeight.Black,
            color = color,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(color.copy(alpha = 0.9f), Offset.Zero, 36f)),
        )
    }
}
