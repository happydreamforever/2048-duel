@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.duel2048.app.ui.landscape

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.*
import com.duel2048.app.ui.components.formatClock
import com.duel2048.app.ui.landscape.LandscapeText as Text
import com.duel2048.app.ui.screens.PuzzleAnswerControls
import com.duel2048.app.ui.theme.*
import com.duel2048.shared.protocol.SavedGameType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun LandscapeMini(vm: MainViewModel) {
    val state by vm.mini.collectAsStateWithLifecycle()
    val s = state ?: return
    val p = LocalPalette.current
    var leave by remember { mutableStateOf(false) }
    val requestLeave: () -> Unit = {
        if (s.mode == "train" || s.status == "over") vm.closeMini() else leave = true
    }
    BackHandler(onBack = requestLeave)
    val game =
        when (s.game) {
            SavedGameType.WORD -> HubGame.WORD
            SavedGameType.WORD_PUZZLE -> HubGame.WORD_PUZZLE
            else -> HubGame.MATH
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.title + " · " + if (s.mode == "train") "Practice" else s.opponent,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.textPrimary,
                    maxLines = 1)
                Text("${s.secondsLeft}s", color = p.gold, fontWeight = FontWeight.Bold)
                TextButton(onClick = requestLeave) {
                    Text(if (s.status == "over") "Dashboard" else "Leave", color = p.danger)
                }
            }
            if (s.status == "waiting") {
                EmptyState(
                    if (s.opponent == "waiting") "Waiting for an opponent" else "Match starting…",
                    if (s.opponent == "waiting") "The competition starts when both players join."
                    else "${s.opponent} · ${s.secondsLeft}")
            } else if (s.status == "over") {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                        Art(game, Modifier.weight(.35f).fillMaxHeight())
                        Column(
                            Modifier.weight(.65f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val draw =
                                    s.game == SavedGameType.WORD_PUZZLE &&
                                        s.mode != "train" &&
                                        s.score == s.opponentScore
                                Text(
                                    if (draw) "Draw"
                                    else if (s.won) "You won!" else "Round finished",
                                    color = if (s.won) p.success else p.gold,
                                    style = MaterialTheme.typography.headlineMedium)
                                if (s.game == SavedGameType.WORD_PUZZLE)
                                    Text(
                                        "You ${s.score} · ${s.opponent} ${s.opponentScore}",
                                        color = p.textPrimary)
                                if (s.solution.isNotBlank())
                                    Text("Solution: " + s.solution, color = p.textSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ArcadeAction("Play Again") { vm.playMiniAgain() }
                                    ArcadeAction("Dashboard") { vm.closeMini() }
                                }
                            }
                    }
            } else {
                if (s.game != SavedGameType.WORD_PUZZLE)
                    Text(
                        s.prompt,
                        color = p.textPrimary,
                        style =
                            if (s.game == SavedGameType.MATH)
                                MaterialTheme.typography.headlineMedium
                            else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                else
                    Text(
                        "${s.score} points · ${s.opponentScore} opponent",
                        color = p.gold,
                        style = MaterialTheme.typography.labelMedium)
                if (s.hint.isNotBlank())
                    Text(s.hint, color = p.accent, style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).fillMaxWidth()) { PuzzleAnswerControls(vm, s) }
            }
        }
    if (leave)
        AlertDialog(
            onDismissRequest = { leave = false },
            title = { Text("Leave this match?") },
            text = { Text("The online round and its timer continue when you leave.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        leave = false
                        vm.closeMini()
                    }) {
                        Text("Leave")
                    }
            },
            dismissButton = { TextButton(onClick = { leave = false }) { Text("Stay") } })
}

@Composable
fun LandscapeQueue(vm: MainViewModel, isCube: Boolean) {
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val opponent = if (isCube) cube.privateOpponent else duel.privateOpponent
    val started = if (isCube) cube.searchStartedAt else duel.searchStartedAt
    val fallback = if (isCube) cube.botFallbackMs else duel.botFallbackMs
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val cancel: () -> Unit = { if (isCube) vm.cancelCubeSearch() else vm.cancelSearch() }
    BackHandler(onBack = cancel)
    Row(
        Modifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Art(modifier = Modifier.weight(.3f).fillMaxHeight())
            Column(
                Modifier.weight(.7f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (opponent.isNotBlank()) "Waiting for $opponent"
                        else "Finding an opponent",
                        color = LocalPalette.current.textPrimary,
                        style = MaterialTheme.typography.headlineSmall)
                    Text(
                        (if (isCube) "Cube" else "2048") + " · " + s.accountName,
                        color = LocalPalette.current.accent)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        formatClock((now - started).coerceAtLeast(0)),
                        color = LocalPalette.current.gold)
                    Text(
                        if (opponent.isNotBlank()) "Private invitation · No bot fallback"
                        else if (fallback > 0)
                            "Public queue · Bot fallback in ${((fallback-(now-started)).coerceAtLeast(0)+999)/1000}s"
                        else "Connecting to the server…",
                        color = LocalPalette.current.textSecondary)
                    ArcadeAction("Cancel Search", onClick = cancel)
                    Text(
                        "Entry is charged only when the competition starts.",
                        color = LocalPalette.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
        }
}

@Composable
fun LandscapeResult(vm: MainViewModel, isCube: Boolean) {
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val won = if (isCube) cube.won else duel.won
    val draw = if (isCube) cube.draw else duel.draw
    val p = LocalPalette.current
    BackHandler { vm.goHome() }
    Row(
        Modifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Art(if (isCube) HubGame.CUBE else HubGame.TILES, Modifier.weight(.35f).fillMaxHeight())
            Column(
                Modifier.weight(.65f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (draw) "Draw" else if (won) "You won!" else "Round finished",
                        color = if (won) p.success else p.gold,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text(
                        if (isCube) cube.opponent?.name.orEmpty()
                        else duel.opponent.info?.name.orEmpty(),
                        color = p.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Metric(
                            if (isCube) "Your moves" else "Your score",
                            (if (isCube) cube.myMoves else duel.me.score).toString(),
                            Modifier.weight(1f))
                        Metric(
                            if (isCube) "Opponent moves" else "Opponent score",
                            (if (isCube) cube.oppMoves else duel.opponent.score).toString(),
                            Modifier.weight(1f))
                        Metric("Wallet balance", s.coins.toString(), Modifier.weight(1f))
                    }
                    Text(
                        if (isCube) cube.result?.reason?.name.orEmpty()
                        else duel.result?.reason?.name.orEmpty(),
                        color = p.textSecondary,
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArcadeAction("Play Again") {
                            if (isCube) vm.playCubeAgain() else vm.playAgain()
                        }
                        ArcadeAction("Dashboard") { vm.goHome() }
                    }
                }
        }
}

@Composable
fun LandscapeWatch(vm: MainViewModel) {
    val live by vm.liveGames.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    val chosen = live.firstOrNull { it.id == selected }
    val p = LocalPalette.current
    var frame by remember(selected) { mutableStateOf<com.duel2048.shared.social.WatchFrame?>(null) }
    var status by remember(selected) { mutableStateOf("Connecting…") }
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshLive()
            delay(2000)
        }
    }
    LaunchedEffect(selected, s.serverUrl) {
        val id = selected ?: return@LaunchedEffect
        while (true) {
            try {
                com.duel2048.app.net.WatchClient().frames(s.serverUrl, id).collect {
                    frame = it
                    status = "Live"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Reconnecting…"
            }
            delay(2000)
        }
    }
    BackHandler { vm.goHome() }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        UtilityHeader(if (selected == null) "Watch Live" else "Watching · read-only", vm::goHome)
        if (selected == null) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        filter.isBlank(),
                        {
                            filter = ""
                            vm.openWatch()
                        },
                        label = { Text("All games") })
                    HubGame.entries.forEach { g ->
                        FilterChip(
                            filter == g.gameKey(),
                            {
                                filter = g.gameKey()
                                vm.openWatch(g)
                            },
                            label = { Text(com.duel2048.app.ui.screens.gameTitle(g)) })
                    }
                }
            val rooms = live.filter { filter.isBlank() || it.game == filter }
            if (rooms.isEmpty())
                EmptyState(
                    "No live rooms right now",
                    "Watching is free. Private invitations are not listed.",
                    "Refresh") {
                        vm.refreshLive()
                    }
            else
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rooms, key = { it.id }) { room ->
                        NeonPane(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Art(
                                    runCatching { gameFromKey(room.game) }
                                        .getOrDefault(HubGame.TILES),
                                    Modifier.size(52.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        room.game.uppercase() +
                                            if (room.demo) " · DEMO" else " · LIVE",
                                        color = p.accent)
                                    Text(
                                        "${room.playerA} ${room.scoreA}  vs  ${room.playerB} ${room.scoreB}",
                                        color = p.textPrimary)
                                }
                                ArcadeAction("Watch") {
                                    selected = room.id
                                    vm.focusWatch(room.id)
                                }
                            }
                        }
                    }
                }
        } else {
            val room = frame?.room ?: chosen
            if (room == null)
                EmptyState(
                    "This room is no longer live", "Return to the live-room list.", "Live rooms") {
                        selected = null
                    }
            else
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeonPane(Modifier.weight(.4f).fillMaxHeight()) {
                            Art(
                                runCatching { gameFromKey(room.game) }.getOrDefault(HubGame.TILES),
                                Modifier.weight(1f).fillMaxWidth())
                            Text(
                                if (room.demo) "DEMO · simulated players" else status,
                                color = p.accent)
                            Text(
                                "${room.playerA}: ${room.scoreA}\n${room.playerB}: ${room.scoreB}",
                                color = p.textPrimary)
                            TextButton(onClick = { selected = null }) { Text("Live rooms") }
                        }
                        NeonPane(Modifier.weight(.6f).fillMaxHeight()) {
                            val grid = frame?.grid.orEmpty()
                            if (grid.isEmpty()) {
                                Text(
                                    "Live competition scores",
                                    color = p.textPrimary,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${frame?.event.orEmpty()} · ${frame?.seconds?:0}s",
                                    color = p.textSecondary)
                                Text(
                                    "Read-only server updates. Player chat stays private.",
                                    color = p.textSecondary)
                            } else
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                    val side = minOf(maxWidth, maxHeight)
                                    Column(Modifier.size(side)) {
                                        grid.forEachIndexed { r, row ->
                                            Row(Modifier.weight(1f)) {
                                                row.forEachIndexed { c, ch ->
                                                    Box(
                                                        Modifier.weight(1f)
                                                            .fillMaxHeight()
                                                            .padding(2.dp)
                                                            .background(
                                                                if (r * grid.size + c in
                                                                    frame?.selected.orEmpty())
                                                                    p.accent.copy(alpha = .3f)
                                                                else p.surface),
                                                        contentAlignment = Alignment.Center) {
                                                            Text(
                                                                ch.toString(),
                                                                color = p.textPrimary)
                                                        }
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }
        }
    }
}
