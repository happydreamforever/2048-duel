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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.*
import com.duel2048.app.ui.landscape.LandscapeText as Text
import com.duel2048.app.ui.screens.*
import com.duel2048.app.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun LandscapeInvitations(vm: MainViewModel, screen: Screen.Challenges) {
    val p = LocalPalette.current
    val invitations by vm.challenges.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.challengeBusy.collectAsStateWithLifecycle()
    val error by vm.challengeError.collectAsStateWithLifecycle()
    val joining by vm.joiningChallenge.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var opponent by
        rememberSaveable(screen.game, screen.opponent) { mutableStateOf(screen.opponent) }
    var tab by rememberSaveable { mutableStateOf("All") }
    var game by rememberSaveable(screen.game) { mutableStateOf(screen.game ?: HubGame.MATH) }
    var level by
        rememberSaveable(screen.game, screen.difficulty) {
            mutableStateOf(screen.difficulty.ifBlank { "easy" })
        }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        vm.refreshPlayers()
        while (true) {
            clock = System.currentTimeMillis()
            delay(1000)
        }
    }
    val close: () -> Unit = {
        if (joining != null) vm.respondChallenge(checkNotNull(joining), "cancel") else vm.goHome()
    }
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize().padding(12.dp).imePadding()) {
        UtilityHeader(if (screen.game != null) "Play a Friend" else "Invitations", close)
        if (error != null)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    error.orEmpty(),
                    Modifier.weight(1f),
                    color = p.danger,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = {
                        vm.clearChallengeError()
                        vm.refreshChallenges()
                    }) {
                        Text("Retry")
                    }
            }
        if (screen.game != null && joining == null)
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonPane(Modifier.weight(.4f).fillMaxHeight()) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Your player name: ${settings.accountName}",
                                    color = p.textSecondary)
                                OutlinedTextField(
                                    opponent,
                                    { opponent = it.take(16) },
                                    label = { Text("Opponent name") },
                                    singleLine = true,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth())
                                players
                                    .filter {
                                        it.name != settings.accountName &&
                                            opponent.isNotBlank() &&
                                            it.name.contains(opponent, true)
                                    }
                                    .take(4)
                                    .forEach { player ->
                                        TextButton(
                                            onClick = { opponent = player.name }, enabled = !busy) {
                                                Text(
                                                    player.name +
                                                        if (player.online) " · Online"
                                                        else " · Offline")
                                            }
                                    }
                                Text(
                                    "Invitations expire after 2 minutes.",
                                    color = p.textSecondary,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                    }
                    NeonPane(Modifier.weight(.6f).fillMaxHeight()) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        HubGame.entries.forEach { g ->
                                            FilterChip(
                                                selected = g == game,
                                                onClick = {
                                                    game = g
                                                    level =
                                                        when (g) {
                                                            HubGame.MATH -> "easy"
                                                            HubGame.WORD,
                                                            HubGame.WORD_PUZZLE -> "4"
                                                            else -> "normal"
                                                        }
                                                },
                                                label = { Text(gameTitle(g)) })
                                        }
                                    }
                                val levels =
                                    when (game) {
                                        HubGame.MATH ->
                                            listOf("easy", "medium", "hard", "very_hard")
                                        HubGame.WORD ->
                                            com.duel2048.shared.social.Puzzles.wordLengths.map(
                                                Int::toString)
                                        HubGame.WORD_PUZZLE ->
                                            com.duel2048.shared.social.WordGrid.sizes.map(
                                                Int::toString)
                                        else -> listOf("normal")
                                    }
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        levels.forEach { id ->
                                            FilterChip(
                                                selected = level == id,
                                                onClick = { level = id },
                                                label = {
                                                    Text(
                                                        if (game == HubGame.WORD_PUZZLE) "$id × $id"
                                                        else difficultyLabel(id))
                                                })
                                        }
                                    }
                                Text(
                                    "${settings.accountName}  vs  ${opponent.ifBlank {"…"}}",
                                    color = p.textPrimary,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "10 coins per player · No charge until both join",
                                    color = p.gold,
                                    style = MaterialTheme.typography.bodySmall)
                                ArcadeAction(
                                    if (busy) "Sending…" else "Send Invitation",
                                    enabled =
                                        !busy &&
                                            opponent.trim().length >= 3 &&
                                            !opponent.equals(settings.accountName, true)) {
                                        focus.clearFocus()
                                        vm.createChallenge(game, level, opponent)
                                    }
                            }
                    }
                }
        else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf("All", "Incoming", "Sent").forEach { label ->
                    FilterChip(
                        tab == label,
                        { tab = label },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                ArcadeAction("Invite a Player", enabled = joining == null && !busy) {
                    vm.openChallenges(HubGame.MATH, "easy")
                }
            }
            val shown =
                invitations
                    .filter { tab == "All" || it.incoming == (tab == "Incoming") }
                    .sortedByDescending { it.expiresAt }
            if (shown.isEmpty())
                EmptyState(
                    "No invitations yet",
                    "Choose a player by account name to start a private match.")
            else
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { invite ->
                        val other = if (invite.incoming) invite.from else invite.to
                        NeonPane(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                    Art(gameFromKey(invite.game), Modifier.size(48.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            other + " · " + gameTitle(gameFromKey(invite.game)),
                                            color = p.textPrimary)
                                        Text(
                                            invite.status.replaceFirstChar { it.uppercase() } +
                                                if (invite.status in listOf("pending", "accepted"))
                                                    " · ${((invite.expiresAt-clock)/1000).coerceAtLeast(0)}s"
                                                else "",
                                            color = p.textSecondary,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (invite.youReady && invite.status == "accepted")
                                        Text("Waiting for $other", color = p.gold)
                                }
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (invite.status == "pending" && invite.incoming) {
                                        ArcadeAction("Accept", enabled = !busy && joining == null) {
                                            vm.respondChallenge(invite.id, "accept")
                                        }
                                        TextButton(
                                            onClick = { vm.respondChallenge(invite.id, "decline") },
                                            enabled = !busy && joining == null) {
                                                Text("Decline")
                                            }
                                    }
                                    if (invite.status in listOf("accepted", "playing"))
                                        ArcadeAction(
                                            if (invite.status == "playing") "Return to Match"
                                            else "Join Match",
                                            enabled = !busy && joining == null) {
                                                vm.joinChallenge(invite)
                                            }
                                    if (invite.status in listOf("pending", "accepted") &&
                                        (!invite.incoming || invite.status == "accepted"))
                                        TextButton(
                                            onClick = { vm.respondChallenge(invite.id, "cancel") },
                                            enabled =
                                                !busy &&
                                                    (joining == null || joining == invite.id)) {
                                                Text("Cancel", color = p.danger)
                                            }
                                    if (invite.status == "completed" && joining == null)
                                        TextButton(
                                            onClick = {
                                                vm.openChallenges(
                                                    gameFromKey(invite.game),
                                                    invite.difficulty,
                                                    other)
                                            },
                                            enabled = !busy) {
                                                Text("Invite Again")
                                            }
                                }
                        }
                    }
                }
        }
    }
}
