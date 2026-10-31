@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.duel2048.app.ui.landscape

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.*
import com.duel2048.app.ui.errorText
import com.duel2048.app.ui.landscape.LandscapeText as Text
import com.duel2048.app.ui.screens.difficultyLabel
import com.duel2048.app.ui.screens.gameTitle
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.app.update.AppUpdater

@Composable
fun LandscapeDashboard(vm: MainViewModel) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val update by AppUpdater.status.collectAsStateWithLifecycle()
    val overlay = LocalHubOverlay.current
    var selected by rememberSaveable { mutableStateOf("PVP") }
    var friend by rememberSaveable { mutableStateOf(false) }
    val mode = HubMode.valueOf(selected)
    val fee =
        if (friend || mode == HubMode.PVP) "Entry 10 coins"
        else if (mode == HubMode.BOT) "Entry 5 coins" else "Free"
    LaunchedEffect(settings.serverUrl) { AppUpdater.checkAndInstall(context, settings.serverUrl) }
    val error = duel.error ?: cube.error
    if (error != null || banner != null)
        AlertDialog(
            onDismissRequest = {
                vm.clearError()
                vm.clearBanner()
            },
            title = { Text("Unable to start") },
            text = {
                Text(
                    error?.let { errorText(it) }
                        ?: if (banner == "need_coins") "Not enough coins for this competition."
                        else "Please check your connection and try again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearError()
                        vm.clearBanner()
                    }) {
                        Text("OK")
                    }
            })
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 330.dp
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (update != null)
                    Text(
                        update.orEmpty(),
                        color = p.gold,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1)
                if (!compactHeight)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Ready for your next win?",
                                color = p.textPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text(
                                "Choose a mode. Pick a game. Play.",
                                color = p.textSecondary,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(fee, color = p.gold, style = MaterialTheme.typography.labelMedium)
                    }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                        listOf(
                                "Practice" to HubMode.TRAIN,
                                "Vs Bot" to HubMode.BOT,
                                "Quick PvP" to HubMode.PVP,
                                "Play a Friend" to null,
                                "Watch Live" to HubMode.WATCH)
                            .forEach { (label, m) ->
                                FilterChip(
                                    selected = if (m == null) friend else !friend && mode == m,
                                    onClick = {
                                        friend = m == null
                                        if (m != null) selected = m.name
                                    },
                                    label = {
                                        Text(label, style = MaterialTheme.typography.labelMedium)
                                    },
                                    modifier =
                                        Modifier.heightIn(min = 48.dp)
                                            .testTag("mode:" + (m?.name ?: "FRIEND")))
                            }
                        if (compactHeight)
                            Text(fee, color = p.gold, style = MaterialTheme.typography.labelSmall)
                    }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val cardWidth = maxOf(120.dp, (maxWidth - 32.dp) / 5)
                    Row(
                        Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HubGame.entries.forEach { game ->
                                GameCard(
                                    vm,
                                    game,
                                    Modifier.width(cardWidth).fillMaxHeight(),
                                    mode,
                                    friend)
                            }
                        }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                                "Solo 2048" to "solo",
                                "Leaderboard" to "leaderboard",
                                "Match Results" to "history",
                                "Buy Coins" to "shop",
                                "How to Play" to "help",
                                "Support & Report" to "support",
                                "Connection" to "connection")
                            .forEach { (label, key) ->
                                TextButton(
                                    onClick = { overlay(key) },
                                    modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(label, style = MaterialTheme.typography.labelMedium)
                                    }
                            }
                    }
            }
    }
}

@Composable
private fun GameCard(
    vm: MainViewModel,
    game: HubGame,
    modifier: Modifier,
    mode: HubMode,
    friend: Boolean
) {
    val p = LocalPalette.current
    var difficulty by
        rememberSaveable(game.name) {
            mutableStateOf(
                when (game) {
                    HubGame.MATH -> "easy"
                    HubGame.WORD,
                    HubGame.WORD_PUZZLE -> "4"
                    else -> "normal"
                })
        }
    var menu by remember { mutableStateOf(false) }
    val options =
        when (game) {
            HubGame.MATH -> listOf("easy", "medium", "hard", "very_hard")
            HubGame.WORD -> com.duel2048.shared.social.Puzzles.wordLengths.map(Int::toString)
            HubGame.WORD_PUZZLE -> com.duel2048.shared.social.WordGrid.sizes.map(Int::toString)
            else -> listOf("easy", "normal", "hard")
        }
    val selectable =
        game !in listOf(HubGame.TILES, HubGame.CUBE) ||
            game == HubGame.TILES && mode == HubMode.TRAIN && !friend
    NeonPane(modifier, gameAccent(game)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Art(game, Modifier.fillMaxSize())
        }
        Box {
            TextButton(
                onClick = { menu = true },
                enabled = selectable,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Column {
                        Text(
                            gameTitle(game),
                            color = p.textPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1)
                        Text(
                            if (!selectable) (if (game == HubGame.CUBE) "3 × 3" else "Classic")
                            else
                                (if (game == HubGame.WORD_PUZZLE) "$difficulty × $difficulty"
                                else difficultyLabel(difficulty)) + " ▾",
                            color = gameAccent(game),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1)
                    }
                }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { id ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (game == HubGame.WORD_PUZZLE) "$id × $id"
                                else difficultyLabel(id))
                        },
                        onClick = {
                            difficulty = id
                            menu = false
                        })
                }
            }
        }
        ArcadeAction(
            if (friend) "Invite Player"
            else
                when (mode) {
                    HubMode.TRAIN -> "Practice"
                    HubMode.BOT -> "Play Bot"
                    HubMode.PVP -> "Find Match"
                    HubMode.WATCH -> "Watch"
                },
            Modifier.fillMaxWidth().testTag("play:" + game.name),
            accent = gameAccent(game)) {
                if (friend) vm.openChallenges(game, difficulty)
                else vm.startHub(game, mode, difficulty)
            }
    }
}
