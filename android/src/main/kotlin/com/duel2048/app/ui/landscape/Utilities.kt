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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.*
import com.duel2048.app.ui.Languages
import com.duel2048.app.ui.landscape.LandscapeText as Text
import com.duel2048.app.ui.screens.gameTitle
import com.duel2048.app.ui.theme.*
import kotlinx.coroutines.*

@Composable
fun LandscapeUtilities(
    vm: MainViewModel,
    key: String,
    onClose: () -> Unit,
    activeMatch: Boolean = false
) {
    BackHandler(onBack = onClose)
    val p = LocalPalette.current
    val open = LocalHubOverlay.current
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        UtilityHeader(
            when (key) {
                "settings" -> "Settings"
                "notes" -> "Notifications"
                "shop" -> "Buy Coins"
                "help" -> "How to Play"
                "profile" -> "My Profile"
                "connection" -> "Connection"
                "support" -> "Support & Report"
                "history" -> "Match Results"
                "leaderboard" -> "Leaderboard"
                "payment" -> "Payment Help"
                else -> "Players"
            },
            onClose)
        Box(Modifier.weight(1f)) {
            when (key) {
                "settings" -> Preferences(vm, activeMatch)
                "notes" -> Notices(vm)
                "shop" -> CoinShop(vm)
                "payment" ->
                    EmptyState(
                        "Purchase requests",
                        "Choose 50, 200 or 500 coins. Request a pack, follow support’s payment instructions, and wait for administrator approval. Coins are added only after payment is verified.",
                        "Support & Report") {
                            open("support")
                        }
                "profile" -> PlayerProfile(vm)
                "help" -> Instructions(vm)
                "connection" -> Connection(vm, activeMatch)
                "support" -> Support(vm, onClose)
                "leaderboard" -> Rankings(vm)
                "history" -> HistoryList(vm)
                else -> Players(vm)
            }
        }
    }
}

@Composable
private fun Preferences(vm: MainViewModel, active: Boolean) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val open = LocalHubOverlay.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonPane(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Appearance",
                    color = LocalPalette.current.textPrimary,
                    style = MaterialTheme.typography.titleMedium)
                Palettes.all.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { theme ->
                            FilterChip(
                                selected = s.themeId == theme.id,
                                onClick = { vm.updateSettings { it.copy(themeId = theme.id) } },
                                label = { Text(theme.id.replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
                Text("Language", color = LocalPalette.current.textPrimary)
                Languages.all.forEach { (tag, label) ->
                    FilterChip(
                        selected = s.language == tag,
                        onClick = { vm.updateSettings { it.copy(language = tag) } },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        NeonPane(Modifier.weight(1f).fillMaxHeight()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceToggle("Sound effects", s.sound) { v ->
                        vm.updateSettings { it.copy(sound = v) }
                    }
                    PreferenceToggle("Haptics", s.haptics) { v ->
                        vm.updateSettings { it.copy(haptics = v) }
                    }
                    PreferenceToggle("Reduce effects", s.lowEffects) { v ->
                        vm.updateSettings { it.copy(lowEffects = v) }
                    }
                    ArcadeAction("Connection", enabled = !active) { open("connection") }
                    ArcadeAction("Support & Report", enabled = !active) { open("support") }
                    Text(
                        "Changes saved automatically",
                        color = LocalPalette.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}

@Composable
private fun PreferenceToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = LocalPalette.current.textPrimary)
            Switch(value, onChange)
        }
}

@Composable
private fun Notices(vm: MainViewModel) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshNotes() }
    if (notes.isEmpty())
        EmptyState("No notifications yet", "Server announcements will appear here.", "Refresh") {
            vm.refreshNotes()
        }
    else
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes.reversed(), key = { it.id }) { note ->
                NeonPane(Modifier.fillMaxWidth()) {
                    Text(
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.at)),
                        color = LocalPalette.current.accent,
                        style = MaterialTheme.typography.labelSmall)
                    Text(note.text, color = LocalPalette.current.textPrimary)
                }
            }
        }
}

@Composable
private fun CoinShop(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.purchaseMessage.collectAsStateWithLifecycle()
    val open = LocalHubOverlay.current
    var pack by rememberSaveable { mutableStateOf(200) }
    var confirm by remember { mutableStateOf(false) }
    val pending by vm.purchaseBusy.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(.6f).verticalScroll(rememberScrollState())) {
            Text(
                "Choose a coin pack",
                color = LocalPalette.current.textPrimary,
                style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 200, 500).forEach { n ->
                    NeonPane(
                        Modifier.weight(1f),
                        if (n == pack) LocalPalette.current.accent else LocalPalette.current.gold) {
                            Text("●", color = LocalPalette.current.gold, fontSize = 40.sp)
                            Text(
                                "$n coins",
                                color = LocalPalette.current.textPrimary,
                                style = MaterialTheme.typography.titleMedium)
                            ArcadeAction(
                                if (n == pack) "Selected" else "Select", enabled = !pending) {
                                    pack = n
                                }
                        }
                }
            }
            if (message.isNotEmpty())
                Text(message, Modifier.padding(top = 12.dp), color = LocalPalette.current.gold)
        }
        NeonPane(Modifier.weight(.4f).fillMaxHeight()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Purchase request",
                        color = LocalPalette.current.textPrimary,
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Selected pack: $pack coins\nCurrent balance: ${s.coins}\nAfter approval: ${s.coins.toLong()+pack}",
                        color = LocalPalette.current.textPrimary)
                    Text(
                        "Coins are credited after an administrator verifies payment.",
                        color = LocalPalette.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall)
                    ArcadeAction(
                        if (pending) "Sending…" else "Request $pack coins", enabled = !pending) {
                            confirm = true
                        }
                    TextButton(onClick = { open("payment") }) { Text("Payment help") }
                }
        }
    }
    if (confirm)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Request $pack coins?") },
            text = {
                Text(
                    "This sends a purchase request. It does not charge your account or add coins immediately.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        vm.buyCoins(pack)
                    }) {
                        Text("Send Request")
                    }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
private fun PlayerProfile(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    val open = LocalHubOverlay.current
    val clipboard = LocalClipboardManager.current
    var logout by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val total = s.accWins + s.accLosses + s.accDraws
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonPane(Modifier.weight(.33f).fillMaxHeight()) {
            Art(modifier = Modifier.fillMaxWidth().weight(1f))
            Text(
                if (s.loggedIn) s.accountName else "Guest",
                color = p.textPrimary,
                style = MaterialTheme.typography.titleLarge)
            ArcadeAction(
                if (copied) "Copied" else if (s.loggedIn) "Copy player name" else "Sign In") {
                    if (s.loggedIn) {
                        clipboard.setText(AnnotatedString(s.accountName))
                        copied = true
                    } else vm.openLogin()
                }
            Text("${s.coins} coins", color = p.gold, modifier = Modifier.padding(top = 6.dp))
        }
        Column(
            Modifier.weight(.67f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Your competition record",
                    color = p.textPrimary,
                    style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Matches", total.toString(), Modifier.weight(1f))
                    Metric("Wins", s.accWins.toString(), Modifier.weight(1f))
                    Metric("Losses", s.accLosses.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Draws", s.accDraws.toString(), Modifier.weight(1f))
                    Metric(
                        "Win rate",
                        if (total == 0) "—" else "${s.accWins*100L/total}%",
                        Modifier.weight(1f))
                    Metric("Best score", s.accBestScore.toString(), Modifier.weight(1f))
                }
                Text("Progress score  ${s.score}", color = p.accent)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArcadeAction("Settings") { open("settings") }
                    if (s.loggedIn) ArcadeAction("Sign Out", accent = p.danger) { logout = true }
                }
            }
    }
    if (logout)
        AlertDialog(
            onDismissRequest = { logout = false },
            title = { Text("Sign out?") },
            text = { Text("You can still practice as a guest.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        logout = false
                        vm.logout()
                        vm.openLogin()
                    }) {
                        Text("Sign Out")
                    }
            },
            dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancel") } })
}

@Composable
private fun Instructions(vm: MainViewModel) {
    var game by rememberSaveable { mutableStateOf(HubGame.WORD_PUZZLE.name) }
    val selected = HubGame.valueOf(game)
    val p = LocalPalette.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(160.dp).verticalScroll(rememberScrollState())) {
            HubGame.entries.forEach { g ->
                FilterChip(
                    selected = g == selected,
                    onClick = { game = g.name },
                    label = { Text(gameTitle(g)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
        }
        NeonPane(Modifier.weight(.4f).fillMaxHeight(), gameAccent(selected)) {
            Art(selected, Modifier.fillMaxSize())
        }
        Column(
            Modifier.weight(.6f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    gameTitle(selected),
                    style = MaterialTheme.typography.headlineSmall,
                    color = p.textPrimary)
                Text(
                    when (selected) {
                        HubGame.TILES ->
                            "Swipe to merge matching tiles. Build bigger tiles and outplay your opponent. Online timers and attacks follow the competition rules."
                        HubGame.CUBE ->
                            "Drag a row or column to turn. Drag the background to orbit. Face buttons turn outer layers; M, E and S turn middle slices. Both competitors receive the same scramble."
                        HubGame.MATH ->
                            "Choose one of the four answers, then submit. You have one attempt. The first correct answer wins. Division is exact; results are nonnegative."
                        HubGame.WORD ->
                            "Read the clue. Tap the scrambled letter tiles to build a word, then submit your answer. Letters can be selected in any order."
                        HubGame.WORD_PUZZLE ->
                            "Connect neighboring letters horizontally or vertically. Use at least 3 letters and never reuse a cell within one word. Submit valid words before time runs out. No diagonal connections or repeated word credit."
                    },
                    color = p.textSecondary)
                Text(
                    "PvP entry: 10 coins · Winner payout: 25 coins\nBot entry: 5 coins · Practice and watching: free",
                    color = p.gold,
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArcadeAction("Practice") {
                        vm.startHub(
                            selected,
                            HubMode.TRAIN,
                            when (selected) {
                                HubGame.MATH -> "easy"
                                HubGame.WORD,
                                HubGame.WORD_PUZZLE -> "4"
                                else -> "normal"
                            })
                    }
                }
            }
    }
}

@Composable
private fun Connection(vm: MainViewModel, active: Boolean) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf(s.serverUrl) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val valid =
        runCatching {
                java.net.URI(address).let {
                    it.scheme in listOf("ws", "wss", "http", "https") &&
                        !it.host.isNullOrBlank() &&
                        it.userInfo == null
                }
            }
            .getOrDefault(false)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(.35f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Art(modifier = Modifier.fillMaxSize())
        }
        Column(
            Modifier.weight(.65f).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Connect to your game server",
                    color = LocalPalette.current.textPrimary,
                    style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    address,
                    { address = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    enabled = !active && !busy,
                    modifier = Modifier.fillMaxWidth())
                Text(
                    "Example: ws://192.168.1.10:8080/ws",
                    color = LocalPalette.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArcadeAction(
                        if (busy) "Checking…" else "Test Connection",
                        enabled = valid && !busy && !active) {
                            busy = true
                            scope.launch {
                                status =
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                                val url =
                                                    com.duel2048.app.net.LeaderboardClient.httpBase(
                                                        address) + "/health"
                                                val http =
                                                    okhttp3.OkHttpClient.Builder()
                                                        .callTimeout(
                                                            8,
                                                            java.util.concurrent.TimeUnit.SECONDS)
                                                        .build()
                                                http
                                                    .newCall(
                                                        okhttp3.Request.Builder().url(url).build())
                                                    .execute()
                                                    .use {
                                                        if (it.isSuccessful) "Connection successful"
                                                        else "Server returned HTTP ${it.code}"
                                                    }
                                            }
                                            .getOrDefault(
                                                "Could not reach this server. Check the address and network.")
                                    }
                                busy = false
                            }
                        }
                    ArcadeAction("Save & Reconnect", enabled = valid && !active && !busy) {
                        vm.updateSettings { it.copy(serverUrl = address.trim()) }
                        status = "Server address saved"
                    }
                }
                Text(status, color = LocalPalette.current.accent)
                if (active)
                    Text(
                        "Server changes are disabled during a match.",
                        color = LocalPalette.current.gold)
            }
    }
}

@Composable
private fun Support(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.reportState.collectAsStateWithLifecycle()
    val text by vm.reportDraft.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.resetReport() }
    if (state.sent) {
        EmptyState("Report sent", "Thank you for your feedback.", "Done", onClose)
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(.35f)) {
            Art(modifier = Modifier.weight(1f).fillMaxWidth())
            Text(
                "Need a hand?",
                color = LocalPalette.current.textPrimary,
                style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(.65f).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Your message is sent privately to the administrator.",
                    color = LocalPalette.current.textSecondary)
                OutlinedTextField(
                    text,
                    vm::updateReportDraft,
                    label = { Text("Message") },
                    supportingText = { Text("${text.length}/400") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth())
                if (state.error)
                    Text(
                        "Report failed. Your message is kept; please retry.",
                        color = LocalPalette.current.danger)
                if (state.signInRequired) ArcadeAction("Sign In") { vm.openLogin() }
                else
                    ArcadeAction(
                        if (state.busy) "Sending…" else "Send Report",
                        enabled = !state.busy && text.trim().length >= 3) {
                            vm.sendReport(text)
                        }
            }
    }
}

@Composable
private fun Rankings(vm: MainViewModel) {
    val state by vm.leaderboard.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshLeaderboard() }
    when {
        state.loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error != null ->
            EmptyState("Leaderboard unavailable", "Check your connection and try again.", "Retry") {
                vm.refreshLeaderboard()
            }
        state.entries.isEmpty() ->
            EmptyState("No rankings yet", "Play online competitions to build your record.")
        else ->
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonPane(Modifier.weight(.3f).fillMaxHeight()) {
                    Art(modifier = Modifier.weight(1f).fillMaxWidth())
                    Text("Ranked by wins", color = LocalPalette.current.gold)
                    state.entries.firstOrNull()?.let {
                        Text(
                            "#1  ${it.name}",
                            color = LocalPalette.current.textPrimary,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
                LazyColumn(Modifier.weight(.7f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.entries.size) { i ->
                        val e = state.entries[i]
                        NeonPane(
                            Modifier.fillMaxWidth(),
                            if (e.name == s.accountName) LocalPalette.current.accent
                            else LocalPalette.current.surfaceBorder) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "#${i+1}",
                                            Modifier.width(38.dp),
                                            color = LocalPalette.current.gold)
                                        Text(
                                            e.name,
                                            Modifier.weight(1f),
                                            color = LocalPalette.current.textPrimary)
                                        Text(
                                            "${e.wins} wins  ·  ${e.bestScore}",
                                            color = LocalPalette.current.textSecondary)
                                    }
                            }
                    }
                }
            }
    }
}

@Composable
private fun Players(vm: MainViewModel) {
    val players by vm.players.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshPlayers() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(players) { player ->
            NeonPane(Modifier.fillMaxWidth()) {
                Text(player.name, color = LocalPalette.current.textPrimary)
                Text(
                    if (player.online) "Online" else "Offline",
                    color = LocalPalette.current.textSecondary)
            }
        }
    }
}
