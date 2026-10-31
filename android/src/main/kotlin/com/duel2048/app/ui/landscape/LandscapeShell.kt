@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.duel2048.app.ui.landscape

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.*
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.ui.components.ShimmerTitle
import com.duel2048.app.ui.landscape.LandscapeText as Text
import com.duel2048.app.ui.theme.*
import com.duel2048.shared.social.ChatPhrases
import kotlinx.coroutines.delay

@Composable
fun LandscapeShell(vm: MainViewModel, screen: Screen, content: @Composable () -> Unit) {
    RememberResultHistory(vm)
    val p = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val invites by vm.challenges.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val mini by vm.mini.collectAsStateWithLifecycle()
    val joining by vm.joiningChallenge.collectAsStateWithLifecycle()
    val active =
        when (screen) {
            Screen.Duel -> duel.phase !in listOf(DuelPhase.IDLE, DuelPhase.OVER)
            Screen.CubeDuel -> cube.phase !in listOf(DuelPhase.IDLE, DuelPhase.OVER)
            Screen.MiniPlay -> mini?.status in listOf("waiting", "playing")
            Screen.Matchmaking,
            Screen.CubeMatchmaking -> true
            is Screen.Challenges -> joining != null
            else -> false
        }
    var overlay by rememberSaveable { mutableStateOf("") }
    var launchLoginShown by rememberSaveable { mutableStateOf(false) }
    var chatOpen by rememberSaveable { mutableStateOf(true) }
    val room = vm.pvpChatRoom()
    LaunchedEffect(Unit) {
        if (!launchLoginShown) {
            launchLoginShown = true
            if (!settings.loggedIn && screen == Screen.Home) vm.openLogin()
        }
    }
    LaunchedEffect(screen) { if (screen != Screen.Home) overlay = "" }
    BackHandler(enabled = overlay.isNotEmpty()) { overlay = "" }
    val open: (String) -> Unit = { key ->
        if (!active || key == "settings") {
            if (key == "solo") {
                overlay = ""
                vm.openSolo()
            } else if (key == "invites") {
                overlay = ""
                vm.openChallenges()
            } else {
                overlay = key
                if (key == "notes")
                    vm.updateSettings {
                        it.copy(
                            lastReadNoticeAt = notes.maxOfOrNull { n -> n.at } ?: 0L,
                            lastNoticeServer = it.serverUrl)
                    }
            }
        }
    }
    CompositionLocalProvider(LocalHubOverlay provides open) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            NeonPane(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                if (!active) {
                                    overlay = ""
                                    vm.goHome()
                                }
                            },
                            enabled = !active,
                            contentPadding = PaddingValues(0.dp)) {
                                ShimmerTitle("▦ 2048 DUEL", fontSize = 18.sp, letterSpacing = 0.sp)
                            }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { open("shop") }, enabled = !active) {
                            Text(
                                "● ${if(settings.loggedIn)settings.coins else "—"}  +",
                                color = p.gold,
                                fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { open("invites") }, enabled = !active) {
                            BadgedBox(
                                badge = {
                                    val n =
                                        invites.count {
                                            (it.incoming && it.status == "pending") ||
                                                (it.status == "accepted" && !it.youReady)
                                        }
                                    if (n > 0) Badge { Text(n.toString()) }
                                }) {
                                    Icon(Icons.Default.MailOutline, "Invitations", tint = p.accent)
                                }
                        }
                        IconButton(onClick = { open("notes") }, enabled = !active) {
                            BadgedBox(
                                badge = {
                                    if (notes.any { it.at > settings.lastReadNoticeAt }) Badge()
                                }) {
                                    Icon(
                                        Icons.Default.Notifications, "Notifications", tint = p.gold)
                                }
                        }
                        IconButton(onClick = { open("settings") }) {
                            Icon(Icons.Default.Settings, "Settings", tint = p.accent2)
                        }
                        if (room != null)
                            IconButton(onClick = { chatOpen = !chatOpen }) {
                                Icon(Icons.Default.MailOutline, "Quick chat", tint = p.accent)
                            }
                        TextButton(onClick = { open("profile") }, enabled = !active) {
                            Art(modifier = Modifier.size(34.dp))
                            Text(
                                if (settings.loggedIn) settings.accountName else "Guest",
                                color = p.textPrimary,
                                maxLines = 1)
                        }
                    }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val railWidth = if (maxWidth < 800.dp) 208.dp else 244.dp
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                        if (room != null && chatOpen)
                            QuickChatRail(
                                vm,
                                room,
                                Modifier.width(railWidth)
                                    .fillMaxHeight()
                                    .padding(end = 10.dp, bottom = 8.dp))
                    }
                }
                if (overlay.isNotEmpty())
                    Surface(Modifier.fillMaxSize(), color = p.bgBottom) {
                        LandscapeUtilities(
                            vm, overlay, onClose = { overlay = "" }, activeMatch = active)
                    }
            }
        }
    }
}

@Composable
fun QuickChatRail(vm: MainViewModel, room: String, modifier: Modifier) {
    val p = LocalPalette.current
    val lines by vm.chat.collectAsStateWithLifecycle()
    val error by vm.chatError.collectAsStateWithLifecycle()
    var muted by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(room) {
        while (true) {
            vm.refreshChat(room)
            delay(2000)
        }
    }
    LaunchedEffect(sending) {
        if (sending) {
            delay(1000)
            sending = false
        }
    }
    NeonPane(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Quick Chat",
                Modifier.weight(1f),
                color = p.textPrimary,
                fontWeight = FontWeight.Bold)
            TextButton(onClick = { muted = !muted }, contentPadding = PaddingValues(0.dp)) {
                Text(if (muted) "Unmute" else "Mute", style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!muted)
                    lines
                        .filter { it.room == room }
                        .takeLast(3)
                        .forEach { line ->
                            Text(
                                line.from + " · " + line.text,
                                color =
                                    if (line.from == vm.settings.value.accountName) p.accent
                                    else p.textSecondary,
                                style = MaterialTheme.typography.bodySmall)
                        }
                if (error)
                    Text(
                        "Message failed. Try again.",
                        color = p.danger,
                        style = MaterialTheme.typography.bodySmall)
                ChatPhrases.all.take(8).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        pair.forEach { phrase ->
                            ArcadeAction(phrase, Modifier.weight(1f), enabled = !sending) {
                                sending = true
                                vm.sendChat(room, phrase)
                            }
                        }
                    }
                }
                Text("Emojis", color = p.textSecondary, style = MaterialTheme.typography.labelSmall)
                ChatPhrases.all.drop(8).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { emoji ->
                            TextButton(
                                onClick = {
                                    sending = true
                                    vm.sendChat(room, emoji)
                                },
                                enabled = !sending,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(0.dp)) {
                                    Text(emoji, fontSize = 21.sp)
                                }
                        }
                    }
                }
                Text(
                    "Preset phrases and emojis only",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.labelSmall)
            }
    }
}
