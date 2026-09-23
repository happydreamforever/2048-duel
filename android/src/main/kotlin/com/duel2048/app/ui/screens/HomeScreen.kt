package com.duel2048.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.data.UserSettings
import com.duel2048.app.game.TrainingProfile
import com.duel2048.app.ui.components.Avatar
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.components.ShimmerTitle
import com.duel2048.app.ui.components.StatPill
import com.duel2048.app.ui.errorText
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.app.ui.theme.Palettes
import com.duel2048.shared.protocol.MatchMode
import kotlin.math.sin

@Composable
fun HomeScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    var showSettings by remember { mutableStateOf(false) }
    var showTraining by remember { mutableStateOf(false) }
    var showCubeTraining by remember { mutableStateOf(false) }
    val botName = stringResource(R.string.bot_name)

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoTiles()
            Spacer(Modifier.height(8.dp))
            ShimmerTitle("2048", fontSize = 68.sp)
            Text(
                stringResource(R.string.duel),
                style = MaterialTheme.typography.titleLarge,
                color = palette.textSecondary,
                letterSpacing = 12.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(18.dp))
            AccountRow(settings, onLogin = { vm.openLogin() }, onLogout = { vm.logout() })
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                NeonButton(
                    stringResource(R.string.quick_match),
                    subtitle = stringResource(R.string.quick_match_sub),
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.startDuel(MatchMode.PVP) }
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    stringResource(R.string.practice_bot),
                    subtitle = stringResource(R.string.practice_bot_sub),
                    colors = listOf(palette.accent, palette.orbColors[2]),
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.startDuel(MatchMode.BOT) }
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    stringResource(R.string.training),
                    subtitle = stringResource(R.string.training_sub),
                    colors = listOf(Color(0xFF1F9D6B), Color(0xFF0E6B8A)),
                    modifier = Modifier.fillMaxWidth(),
                ) { showTraining = true }
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    stringResource(R.string.solo_zen),
                    subtitle = stringResource(R.string.solo_sub),
                    colors = listOf(Color(0xFF3B4A66), Color(0xFF1F2A44)),
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.openSolo() }
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    stringResource(R.string.cube_quick_match),
                    subtitle = stringResource(R.string.cube_quick_match_sub),
                    colors = listOf(Color(0xFF6B4EFF), Color(0xFF2D1B69)),
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.startCubeDuel(MatchMode.PVP) }
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    stringResource(R.string.cube_practice_bot),
                    subtitle = stringResource(R.string.cube_practice_bot_sub),
                    colors = listOf(Color(0xFF4A6FA5), Color(0xFF1B3A5C)),
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.startCubeDuel(MatchMode.BOT) }
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    stringResource(R.string.cube_training),
                    subtitle = stringResource(R.string.cube_training_sub),
                    colors = listOf(Color(0xFF1F9D6B), Color(0xFF0E4D3A)),
                    modifier = Modifier.fillMaxWidth(),
                ) { showCubeTraining = true }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (settings.loggedIn) {
                    StatPill(stringResource(R.string.wins), settings.accWins.toString(), valueColor = palette.success)
                    StatPill(stringResource(R.string.losses), settings.accLosses.toString(), valueColor = palette.danger)
                    StatPill(stringResource(R.string.draws), settings.accDraws.toString())
                } else {
                    StatPill(stringResource(R.string.wins), settings.wins.toString(), valueColor = palette.success)
                    StatPill(stringResource(R.string.losses), settings.losses.toString(), valueColor = palette.danger)
                }
                StatPill(stringResource(R.string.best_solo), settings.bestSolo.toString())
            }
            if (!settings.loggedIn) {
                Text(stringResource(R.string.training_record), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            NeonButton(
                "🏆 " + stringResource(R.string.leaderboard),
                subtitle = stringResource(R.string.leaderboard_sub),
                colors = listOf(Color(0xFFB8860B), Color(0xFF7A4B00)),
                modifier = Modifier.fillMaxWidth(),
            ) { vm.openLeaderboard() }
            Spacer(Modifier.height(18.dp))
            ThemeRow(settings.themeId) { id -> vm.updateSettings { it.copy(themeId = id) } }
            Spacer(Modifier.height(10.dp))
            Text(settings.serverUrl, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }

        // Declared after the scrolling column so it stays on top and receives taps.
        IconButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = palette.textSecondary)
        }

        duel.error?.let { err ->
            GlassCard(Modifier.align(Alignment.BottomCenter).padding(16.dp).navigationBarsPadding()) {
                Text(errorText(err), color = palette.danger, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.error_hint), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                TextButton(onClick = { vm.clearError() }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ok), color = palette.accent) }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(settings, onDismiss = { showSettings = false }) { vm.updateSettings(it) }
    }
    if (showTraining) {
        TrainingSheet(
            title = stringResource(R.string.training_title),
            description = stringResource(R.string.training_text),
            onDismiss = { showTraining = false },
        ) { profile ->
            showTraining = false
            vm.startTraining(profile, botName)
        }
    }
    if (showCubeTraining) {
        TrainingSheet(
            title = stringResource(R.string.cube_training_title),
            description = stringResource(R.string.cube_training_text),
            onDismiss = { showCubeTraining = false },
        ) { profile ->
            showCubeTraining = false
            vm.startCubeTraining(profile, botName)
        }
    }
}

@Composable
private fun AccountRow(settings: UserSettings, onLogin: () -> Unit, onLogout: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(settings.accountName.ifBlank { settings.playerName.ifBlank { "?" } }, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (settings.loggedIn) {
                Text(stringResource(R.string.logged_in_as, settings.accountName), color = palette.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    stringResource(R.string.account_stats, settings.accWins, settings.accLosses, settings.accDraws),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                )
            } else {
                Text(stringResource(R.string.not_logged_in), color = palette.textPrimary, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 2)
            }
        }
        TextButton(onClick = if (settings.loggedIn) onLogout else onLogin) {
            Text(stringResource(if (settings.loggedIn) R.string.log_out else R.string.log_in), color = palette.accent, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingSheet(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onStart: (TrainingProfile) -> Unit,
) {
    val palette = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(TrainingProfile.NORMAL) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.bgTop) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.accent)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
            TrainingProfile.all.forEach { profile ->
                val (title, sub) = when (profile.id) {
                    "easy" -> R.string.easy to R.string.easy_sub
                    "hard" -> R.string.hard to R.string.hard_sub
                    else -> R.string.normal to R.string.normal_sub
                }
                val isSelected = profile == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) palette.accent.copy(alpha = 0.18f) else palette.surface)
                        .border(1.dp, if (isSelected) palette.accent else palette.surfaceBorder, RoundedCornerShape(14.dp))
                        .clickable { selected = profile }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(title), color = palette.textPrimary, fontWeight = FontWeight.Bold)
                        Text(stringResource(sub), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    }
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) palette.accent else Color.Transparent)
                            .border(2.dp, if (isSelected) palette.accent else palette.textSecondary, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            NeonButton(stringResource(R.string.start), modifier = Modifier.fillMaxWidth()) { onStart(selected) }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** Four bobbing gradient tiles above the title. */
@Composable
private fun LogoTiles() {
    val palette = LocalPalette.current
    val t by rememberInfiniteTransition(label = "logo").animateFloat(0f, 6.2832f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "t")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(2, 4, 8, 16).forEachIndexed { i, v ->
            val style = palette.tileStyle(v)
            Box(
                Modifier
                    .graphicsLayer { translationY = sin(t + i * 0.9f) * 8.dp.toPx(); rotationZ = sin(t + i) * 4f }
                    .size(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(style.start, style.end)))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(v.toString(), fontWeight = FontWeight.Black, color = style.text, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun ThemeRow(selected: String, onSelect: (String) -> Unit) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Palettes.all.forEach { p ->
                val isSelected = p.id == selected
                Box(
                    Modifier
                        .size(if (isSelected) 42.dp else 36.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(p.accent2, p.accent, p.orbColors[2])))
                        .border(if (isSelected) 3.dp else 1.dp, Color.White.copy(alpha = if (isSelected) 0.95f else 0.3f), CircleShape)
                        .clickable { onSelect(p.id) },
                )
            }
        }
    }
}
