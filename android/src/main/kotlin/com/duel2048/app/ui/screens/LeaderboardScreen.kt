package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.components.Avatar
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.errorText
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.protocol.LeaderboardEntry

@Composable
fun LeaderboardScreen(vm: MainViewModel) {
    val state by vm.leaderboard.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    BackHandler { vm.goHome() }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.goHome() }) { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home), tint = palette.textSecondary) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆 " + stringResource(R.string.leaderboard), style = MaterialTheme.typography.headlineMedium, color = palette.gold)
                Text(stringResource(R.string.leaderboard_top, 50), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            IconButton(onClick = { vm.refreshLeaderboard() }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), tint = palette.textSecondary) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.rank), Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Spacer(Modifier.width(44.dp))
            Text(stringResource(R.string.username), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text(stringResource(R.string.record), Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, textAlign = TextAlign.End)
            Text(stringResource(R.string.best_score), Modifier.width(84.dp), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading && state.entries.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = palette.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.loading), color = palette.textSecondary)
                }
                state.error != null && state.entries.isEmpty() -> GlassCard(Modifier.align(Alignment.Center).fillMaxWidth()) {
                    Text(errorText(state.error!!), color = palette.danger, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(settings.serverUrl, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    NeonButton(stringResource(R.string.retry), modifier = Modifier.fillMaxWidth()) { vm.refreshLeaderboard() }
                }
                state.entries.isEmpty() -> Text(
                    stringResource(R.string.leaderboard_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.entries, key = { _, e -> e.name }) { index, entry ->
                        LeaderboardRow(index + 1, entry, isMe = entry.name.equals(settings.accountName, ignoreCase = true))
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isMe: Boolean) {
    val palette = LocalPalette.current
    val medal = when (rank) {
        1 -> Color(0xFFFFD166)
        2 -> Color(0xFFC0C8D8)
        3 -> Color(0xFFCD7F32)
        else -> null
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isMe) palette.accent.copy(alpha = 0.16f) else palette.surface)
            .border(1.dp, if (isMe) palette.accent else palette.surfaceBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (medal != null) Brush.linearGradient(listOf(medal, medal.copy(alpha = 0.6f))) else Brush.linearGradient(listOf(palette.surfaceBorder, palette.surfaceBorder))),
            contentAlignment = Alignment.Center,
        ) {
            Text(rank.toString(), fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (medal != null) Color(0xFF1A1400) else palette.textPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Avatar(entry.name, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = palette.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                if (isMe) stringResource(R.string.you).uppercase() + " · " + stringResource(R.string.matches_played, entry.matches) else stringResource(R.string.matches_played, entry.matches),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMe) palette.accent else palette.textSecondary,
            )
        }
        Column(Modifier.width(88.dp), horizontalAlignment = Alignment.End) {
            Text("${entry.wins} · ${entry.losses} · ${entry.draws}", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text("${entry.bestTile}", style = MaterialTheme.typography.labelSmall, color = palette.gold)
        }
        Text(entry.bestScore.toString(), Modifier.width(84.dp), color = palette.success, fontWeight = FontWeight.Black, textAlign = TextAlign.End, maxLines = 1)
    }
}
