package com.duel2048.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.HubGame
import com.duel2048.app.HubMode
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.social.CHAT_PHRASES
import com.duel2048.shared.social.Economy
import kotlinx.coroutines.delay

@Composable
fun MoreGamesScreen(vm: MainViewModel) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.more_games), style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.more_games_sub), color = palette.textSecondary)
        HubGame.entries.forEach { game ->
            NeonButton(game.title, subtitle = game.blurb, colors = game.colors, modifier = Modifier.fillMaxWidth()) {
                vm.openGameModes(game)
            }
        }
        TextButton(onClick = { vm.goHome() }) { Text(stringResource(R.string.back), color = palette.accent) }
    }
}

@Composable
fun GameModesScreen(vm: MainViewModel, game: HubGame) {
    val palette = LocalPalette.current
    var difficulty by remember(game) { mutableStateOf(if (game == HubGame.WORD) "4" else "easy") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(game.title, style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.mode_cost, Economy.joinCost("pvp"), Economy.joinCost("bot")), color = palette.textSecondary)
        if (game == HubGame.MATH || game == HubGame.WORD || game == HubGame.CUBE || game == HubGame.CUBE2) {
            Text(stringResource(R.string.difficulty), color = palette.accent, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = if (game == HubGame.WORD) listOf("4", "6") else if (game == HubGame.MATH) listOf("easy", "medium", "hard", "very_hard") else listOf("easy", "medium", "hard")
                options.forEach { id ->
                    val on = id == difficulty
                    Text(
                        difficultyLabel(id),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) palette.accent else palette.surface)
                            .clickable { difficulty = id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (on) Color.White else palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        NeonButton(stringResource(R.string.mode_pvp), subtitle = stringResource(R.string.mode_pvp_sub), modifier = Modifier.fillMaxWidth()) {
            vm.startHub(game, HubMode.PVP, difficulty)
        }
        NeonButton(stringResource(R.string.mode_bot), subtitle = stringResource(R.string.mode_bot_sub), colors = listOf(Color(0xFF4A6FA5), Color(0xFF1B3A5C)), modifier = Modifier.fillMaxWidth()) {
            vm.startHub(game, HubMode.BOT, difficulty)
        }
        NeonButton(stringResource(R.string.mode_train), subtitle = stringResource(R.string.mode_train_sub), colors = listOf(Color(0xFF1F9D6B), Color(0xFF0E6B8A)), modifier = Modifier.fillMaxWidth()) {
            vm.startHub(game, HubMode.TRAIN, difficulty)
        }
        NeonButton(stringResource(R.string.mode_watch), subtitle = stringResource(R.string.mode_watch_sub), colors = listOf(Color(0xFF5C4A7A), Color(0xFF2A2140)), modifier = Modifier.fillMaxWidth()) {
            vm.startHub(game, HubMode.WATCH, difficulty)
        }
        TextButton(onClick = { vm.openMoreGames() }) { Text(stringResource(R.string.back), color = palette.accent) }
    }
}

@Composable
fun WatchScreen(vm: MainViewModel) {
    val palette = LocalPalette.current
    val live by vm.liveGames.collectAsStateWithLifecycle()
    val focus by vm.watchFocus.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshLive()
            delay(2000)
        }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Text(stringResource(R.string.mode_watch), style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        if (live.isEmpty()) {
            Text(stringResource(R.string.watch_empty), color = palette.textSecondary)
        } else {
            val shown = focus?.let { id -> live.find { it.id == id } } ?: live.first()
            GlassCard(Modifier.fillMaxWidth()) {
                Text(shown.game.uppercase(), color = palette.accent, fontWeight = FontWeight.Black)
                Text("${shown.playerA}  ${shown.scoreA}", color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${shown.playerB}  ${shown.scoreB}", color = palette.textSecondary, fontSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
            live.forEach { game ->
                Text(
                    "${game.game} · ${game.playerA} vs ${game.playerB}",
                    modifier = Modifier.fillMaxWidth().clickable { vm.focusWatch(game.id) }.padding(vertical = 8.dp),
                    color = if (game.id == shown.id) palette.accent else palette.textPrimary,
                )
            }
        }
        TextButton(onClick = { vm.openMoreGames() }) { Text(stringResource(R.string.back), color = palette.accent) }
    }
}

@Composable
fun MiniPlayScreen(vm: MainViewModel) {
    val palette = LocalPalette.current
    val mini by vm.mini.collectAsStateWithLifecycle()
    val state = mini
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.loading), color = palette.textSecondary)
        }
        return
    }
    var typed by remember(state.prompt, state.grid) { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.title, color = palette.accent, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(stringResource(R.string.vs_name, state.opponent), color = palette.textSecondary)
        Text("${state.secondsLeft}s", color = palette.gold, fontWeight = FontWeight.Black, fontSize = 32.sp)
        if (state.grid.isNotEmpty()) {
            state.grid.forEach { row ->
                Text(row.toCharArray().joinToString("  "), color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text(state.prompt, color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 28.sp)
        }
        if (state.hint.isNotBlank()) Text(stringResource(R.string.hint_line, state.hint), color = palette.accent)
        if (state.status == "playing") {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = if (state.grid.isEmpty()) KeyboardType.Number else KeyboardType.Text),
                singleLine = true,
            )
            NeonButton(stringResource(R.string.submit_answer), modifier = Modifier.fillMaxWidth()) { vm.submitMini(typed) }
            if (state.guided) {
                TextButton(onClick = { vm.miniHint() }) { Text(stringResource(R.string.hint), color = palette.accent) }
            }
        } else {
            Text(
                if (state.won) stringResource(R.string.you_win) else stringResource(R.string.mini_game_over),
                color = if (state.won) palette.success else palette.danger,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
            )
            if (state.solution.isNotBlank()) Text(stringResource(R.string.solution_line, state.solution), color = palette.gold)
        }
        TextButton(onClick = { vm.closeMini() }) { Text(stringResource(R.string.back), color = palette.accent) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val players by vm.players.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshPlayers() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = palette.bgTop) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.user_search), color = palette.accent, fontWeight = FontWeight.Black)
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(360.dp)) {
                items(players.filter { it.name.contains(query, ignoreCase = true) }) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                        Text(stringResource(if (p.online) R.string.presence_online else R.string.presence_offline), color = if (p.online) palette.success else palette.textSecondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val lines by vm.chat.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshChat()
            delay(2500)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = palette.bgTop) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.chat), color = palette.accent, fontWeight = FontWeight.Black)
            lines.takeLast(8).forEach { line ->
                Text("${line.from}: ${line.text}", color = palette.textPrimary)
            }
            CHAT_PHRASES.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { phrase ->
                        Text(
                            phrase,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.surfaceBorder, RoundedCornerShape(12.dp))
                                .clickable { vm.sendChat(phrase) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            color = palette.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.bgTop) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.coin_shop), color = palette.gold, fontWeight = FontWeight.Black)
            listOf(50, 200, 500).forEach { pack ->
                NeonButton(stringResource(R.string.buy_coins, pack), modifier = Modifier.fillMaxWidth(), colors = listOf(Color(0xFFB8860B), Color(0xFF7A4B00))) {
                    vm.buyCoins(pack)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val notes by vm.notes.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.bgTop) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.admin_notes), color = palette.accent, fontWeight = FontWeight.Black)
            if (notes.isEmpty()) Text(stringResource(R.string.no_notes), color = palette.textSecondary)
            notes.forEach { Text(it.text, color = palette.textPrimary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.bgTop) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.report_admin), color = palette.danger, fontWeight = FontWeight.Black)
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth())
            NeonButton(stringResource(R.string.send_report), modifier = Modifier.fillMaxWidth(), colors = listOf(palette.danger, Color(0xFF5A2030))) {
                vm.sendReport(text)
                onDismiss()
            }
        }
    }
}

@Composable
private fun difficultyLabel(id: String): String = when (id) {
    "easy" -> stringResource(R.string.easy)
    "medium", "normal" -> stringResource(R.string.medium)
    "hard" -> stringResource(R.string.hard)
    "very_hard" -> stringResource(R.string.very_hard)
    "4" -> "4×4"
    "6" -> "6×6"
    else -> id
}
