package com.duel2048.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.Screen
import com.duel2048.app.ui.theme.LocalPalette

/** A single persistent, inset-aware toolbar, outside every page's scrolling content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppChrome(vm: MainViewModel, screen: Screen, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val mini by vm.mini.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf("") }
    val seenNotice = if(settings.lastNoticeServer==settings.serverUrl) settings.lastReadNoticeAt else 0L
    val chatRoom = remember(screen, duel, cube, mini) { vm.pvpChatRoom() }
    val navigatingLocked = when(screen) {
        Screen.Duel -> duel.phase !in listOf(com.duel2048.app.game.DuelPhase.IDLE,com.duel2048.app.game.DuelPhase.OVER)
        Screen.CubeDuel -> cube.phase !in listOf(com.duel2048.app.game.DuelPhase.IDLE,com.duel2048.app.game.DuelPhase.OVER)
        Screen.MiniPlay -> mini?.status in listOf("waiting","playing")
        Screen.Matchmaking,Screen.CubeMatchmaking -> true
        else -> false
    }
    val title = when(screen) {
        Screen.Home -> stringResource(R.string.product_play)
        Screen.Community -> stringResource(R.string.product_community)
        Screen.Profile, Screen.Login -> stringResource(R.string.product_profile)
        Screen.Watch -> stringResource(R.string.mode_watch)
        Screen.Leaderboard -> stringResource(R.string.leaderboard)
        is Screen.GameModes -> gameTitle(screen.game)
        Screen.CubeDuel, Screen.CubeMatchmaking, Screen.CubeResult -> stringResource(R.string.product_cube)
        Screen.MiniPlay -> mini?.title.orEmpty()
        else -> "2048"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).zIndex(1f),
            shape = RoundedCornerShape(20.dp), color = palette.bgTop, tonalElevation = 4.dp, shadowElevation = 6.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.Menu, stringResource(R.string.app_navigation)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if(navigatingLocked) DropdownMenuItem(text = { Text(stringResource(R.string.app_finish_navigation), style = MaterialTheme.typography.bodySmall) }, onClick = {}, enabled = false)
                        DropdownMenuItem(text = { Text(stringResource(R.string.product_play)) }, enabled = !navigatingLocked,
                            leadingIcon = { Icon(Icons.Default.Home, null) }, onClick = { menu = false; vm.goHome() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.product_community)) }, enabled = !navigatingLocked,
                            leadingIcon = { Icon(Icons.Default.Favorite, null) }, onClick = { menu = false; vm.openCommunity() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.mode_watch)) }, enabled = !navigatingLocked,
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { menu = false; vm.openWatch() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.leaderboard)) }, enabled = !navigatingLocked,
                            leadingIcon = { Icon(Icons.Default.Star, null) }, onClick = { menu = false; vm.openLeaderboard() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.product_profile)) }, enabled = !navigatingLocked,
                            leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = { menu = false; vm.openProfile() })
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                    Text("Duel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if(chatRoom != null) IconButton(onClick = { sheet = "chat" }) {
                    Icon(Icons.Default.MailOutline, stringResource(R.string.pvp_chat_title), tint = palette.accent)
                }
                IconButton(onClick = { vm.updateSettings { it.copy(lastReadNoticeAt=notes.maxOfOrNull { note -> note.at } ?: seenNotice,lastNoticeServer=it.serverUrl) }; sheet = "notes" }) {
                    BadgedBox(badge = { if(notes.any { it.at > seenNotice }) Badge() }) {
                        Icon(Icons.Default.Notifications, stringResource(R.string.app_notifications))
                    }
                }
                IconButton(onClick = { sheet = "settings" }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { content() }
    }
    when(sheet) {
        "notes" -> NotesSheet(vm) { sheet = "" }
        "settings" -> SettingsSheet(settings, onDismiss = { sheet = "" }, onReport = { vm.resetReport(); sheet = "report" }, connectionEnabled = !navigatingLocked) { vm.updateSettings(it) }
        "report" -> ReportSheet(vm) { sheet = "" }
        "chat" -> if(chatRoom != null) ChatSheet(vm, chatRoom) { sheet = "" } else LaunchedEffect(Unit) { sheet = "" }
    }
}
