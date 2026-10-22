package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.HubGame
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.components.Avatar
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.components.StatPill
import com.duel2048.app.ui.errorText
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.app.update.AppUpdater
import com.duel2048.shared.protocol.*
import com.duel2048.shared.social.Economy

private enum class ProductTab(val title: Int, val icon: ImageVector) {
    PLAY(R.string.product_play, Icons.Default.Home), SAVES(R.string.product_saves, Icons.Default.List),
    COMMUNITY(R.string.product_community, Icons.Default.Favorite), PROFILE(R.string.product_profile, Icons.Default.Person),
}

@Composable
private fun ProductShell(vm: MainViewModel, tab: ProductTab, subtitle: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    BackHandler(enabled = tab != ProductTab.PLAY) { vm.goHome() }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(tab.title), style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            Box(Modifier.clip(RoundedCornerShape(24.dp)).clickable { vm.openProfile() }.padding(4.dp)) {
                Avatar(settings.accountName.ifBlank { settings.playerName.ifBlank { "?" } }, 42.dp)
            }
        }
        Box(Modifier.weight(1f)) { content() }
        NavigationBar(containerColor = palette.surface, contentColor = palette.accent) {
            ProductTab.entries.forEach { destination ->
                NavigationBarItem(selected = tab == destination,
                    onClick = { when (destination) {
                        ProductTab.PLAY -> vm.goHome(); ProductTab.SAVES -> vm.openSavedGames()
                        ProductTab.COMMUNITY -> vm.openCommunity(); ProductTab.PROFILE -> vm.openProfile()
                    } }, icon = { Icon(destination.icon, null) }, label = { Text(stringResource(destination.title)) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = palette.accent, selectedTextColor = palette.accent,
                        indicatorColor = palette.accent.copy(alpha = 0.12f), unselectedIconColor = palette.textSecondary,
                        unselectedTextColor = palette.textSecondary))
            }
        }
    }
}

@Composable
fun HomeScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val saves by vm.gameSaves.games.collectAsStateWithLifecycle()
    val sync by vm.gameSaves.sync.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val update by AppUpdater.status.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val context = LocalContext.current
    LaunchedEffect(settings.serverUrl) { AppUpdater.checkAndInstall(context, settings.serverUrl) }
    LaunchedEffect(Unit) { vm.syncGames() }
    ProductShell(vm, ProductTab.PLAY, stringResource(R.string.product_play_subtitle)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val error = duel.error ?: cube.error
            if (error != null || banner != null || update != null) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(error?.let { errorText(it) } ?: update ?: stringResource(when (banner) {
                        "need_coins" -> R.string.need_coins; "invalid_save" -> R.string.product_invalid_save; else -> R.string.guest_failed
                    }), color = palette.gold)
                    TextButton(onClick = { vm.clearError(); vm.clearBanner() }) { Text(stringResource(R.string.ok)) }
                }
            }
            val recent = saves.firstOrNull { !it.deleted && !it.completed && it.resumable && it.id !in sync.conflicts }
            if (recent != null) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.product_continue), style = MaterialTheme.typography.labelLarge, color = palette.accent)
                    Text(savedGameTitle(recent.game), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Text(saveDetails(recent), color = palette.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    NeonButton(stringResource(R.string.product_resume), modifier = Modifier.fillMaxWidth()) { vm.resumeSavedGame(recent) }
                }
            }
            item { Text(stringResource(R.string.product_games), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary, fontWeight = FontWeight.Bold) }
            items(HubGame.entries.toList(), key = { it.name }) { game -> GameLibraryCard(game) { vm.openGameModes(game) } }
            item {
                TextButton(onClick = { vm.openLeaderboard() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Star, null, tint = palette.gold); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.leaderboard), color = palette.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun GameLibraryCard(game: HubGame, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(palette.surface).border(1.dp, palette.surfaceBorder, shape)
        .clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(game.colors)), contentAlignment = Alignment.Center) {
            Text(when (game) { HubGame.TILES -> "2048"; HubGame.CUBE -> "3×3"; HubGame.MATH -> "±"; HubGame.WORD -> "Aa" },
                color = androidx.compose.ui.graphics.Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(gameTitle(game), color = palette.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(when (game) {
                HubGame.TILES -> R.string.product_tiles_description; HubGame.CUBE -> R.string.product_cube_description
                HubGame.MATH -> R.string.product_math_description; HubGame.WORD -> R.string.product_word_description
            }), color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ArrowForward, null, tint = palette.textSecondary)
    }
}

@Composable
internal fun gameTitle(game: HubGame): String = savedGameTitle(when (game) {
    HubGame.TILES -> SavedGameType.TILES; HubGame.CUBE -> SavedGameType.CUBE
    HubGame.MATH -> SavedGameType.MATH; HubGame.WORD -> SavedGameType.WORD
})

@Composable
private fun savedGameTitle(game: SavedGameType): String = when (game) {
    SavedGameType.TILES -> "2048"; SavedGameType.CUBE -> stringResource(R.string.product_cube)
    SavedGameType.MATH -> stringResource(R.string.product_math); SavedGameType.WORD -> stringResource(R.string.product_word)
}

@Composable
private fun saveDetails(game: SavedGame): String {
    val mode = stringResource(when {
        game.mode == SavedGameMode.ONLINE || game.roomId.isNotEmpty() -> R.string.product_online
        game.mode == SavedGameMode.SOLO -> R.string.product_solo
        game.guided || game.mode == SavedGameMode.TRAINING -> R.string.training
        else -> R.string.mode_bot
    })
    val detail = if (game.game == SavedGameType.CUBE) stringResource(R.string.product_moves_count, game.played.size)
        else if (game.board != null) stringResource(R.string.product_score_count, checkNotNull(game.board).score)
        else stringResource(R.string.product_seconds_left, game.remainingMs / 1000)
    return "$mode · $detail"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedGamesScreen(vm: MainViewModel) {
    val games by vm.gameSaves.games.collectAsStateWithLifecycle()
    val sync by vm.gameSaves.sync.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    var finished by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedGame?>(null) }
    ProductShell(vm, ProductTab.SAVES, stringResource(R.string.product_saves_subtitle)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(when {
                        sync.conflicts.isNotEmpty() -> R.string.product_conflict; sync.syncing -> R.string.product_syncing
                        sync.offline -> R.string.product_sync_offline; !sync.signedIn -> R.string.product_sync_local
                        sync.pending > 0 -> R.string.product_sync_pending; else -> R.string.product_sync_done
                    }), color = if (sync.offline || sync.conflicts.isNotEmpty()) palette.gold else palette.textSecondary)
                    TextButton(onClick = { if (sync.signedIn) vm.syncGames() else vm.openLogin() }, enabled = !sync.syncing) {
                        Text(stringResource(if (sync.signedIn) R.string.product_sync_now else R.string.log_in))
                    }
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !finished, onClick = { finished = false }, label = { Text(stringResource(R.string.product_in_progress)) })
                FilterChip(selected = finished, onClick = { finished = true }, label = { Text(stringResource(R.string.product_finished)) })
            } }
            val shown = games.filter { !it.deleted && it.completed == finished }
            if (shown.isEmpty()) item { GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.product_no_saves), color = palette.textPrimary, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.product_no_saves_hint), color = palette.textSecondary)
                TextButton(onClick = { vm.goHome() }) { Text(stringResource(R.string.product_play)) }
            } }
            items(shown, key = { it.id }) { game -> GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(savedGameTitle(game.game), color = palette.textPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (game.mode != SavedGameMode.ONLINE) IconButton(onClick = { deleting = game }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.product_delete), tint = palette.textSecondary)
                    }
                }
                Text(saveDetails(game), color = palette.textSecondary)
                if (game.completed && (game.duelResult != null || game.cubeResult != null)) {
                    val winner = game.duelResult?.winnerId ?: game.cubeResult?.winnerId
                    Text(stringResource(when {
                        winner == null -> R.string.draw
                        winner == game.you?.id -> R.string.you_win
                        else -> R.string.defeat
                    }), color = if (winner == game.you?.id) palette.success else palette.textSecondary)
                }
                if (game.updatedAt > 0) Text(android.text.format.DateUtils.getRelativeTimeSpanString(game.updatedAt).toString(), color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                if (game.id in sync.conflicts) {
                    Text(stringResource(R.string.product_conflict), color = palette.gold)
                    Row {
                        TextButton(onClick = { vm.gameSaves.resolve(game.id, true); vm.syncGames() }) { Text(stringResource(R.string.product_use_cloud)) }
                        TextButton(onClick = { vm.gameSaves.resolve(game.id, false); vm.syncGames() }) { Text(stringResource(R.string.product_use_device)) }
                    }
                } else if (!game.completed && game.resumable) {
                    TextButton(onClick = { vm.resumeSavedGame(game) }) { Text(stringResource(R.string.product_resume)) }
                } else if (!game.completed) Text(stringResource(R.string.product_match_unavailable), color = palette.textSecondary)
            } }
        }
    }
    deleting?.let { game -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.product_delete)) },
        text = { Text(stringResource(R.string.product_delete_hint)) },
        confirmButton = { TextButton(onClick = { vm.gameSaves.delete(game.id); deleting = null; vm.syncGames() }) { Text(stringResource(R.string.product_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
fun CommunityScreen(vm: MainViewModel) {
    val palette = LocalPalette.current
    val notes by vm.notes.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf("") }
    ProductShell(vm, ProductTab.COMMUNITY, stringResource(R.string.product_community_subtitle)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { NeonButton(stringResource(R.string.mode_watch), subtitle = stringResource(R.string.mode_watch_sub), modifier = Modifier.fillMaxWidth()) { vm.openWatch() } }
            item { ActionCard(stringResource(R.string.chat), stringResource(R.string.product_chat_hint)) { sheet = "chat" } }
            item { ActionCard(stringResource(R.string.user_search), stringResource(R.string.product_players_hint)) { sheet = "players" } }
            item { ActionCard(stringResource(R.string.leaderboard), stringResource(R.string.leaderboard_sub)) { vm.openLeaderboard() } }
            item { Text(stringResource(R.string.product_updates), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary) }
            if (notes.isEmpty()) item { Text(stringResource(R.string.no_notes), color = palette.textSecondary) }
            items(notes.takeLast(5).reversed(), key = { it.id }) { note -> GlassCard(Modifier.fillMaxWidth()) { Text(note.text, color = palette.textPrimary) } }
        }
    }
    if (sheet == "chat") ChatSheet(vm) { sheet = "" }
    if (sheet == "players") SearchSheet(vm) { sheet = "" }
}

@Composable
fun ProfileScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val context = LocalContext.current
    var sheet by remember { mutableStateOf("") }
    ProductShell(vm, ProductTab.PROFILE, stringResource(R.string.product_profile_subtitle)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { GlassCard(Modifier.fillMaxWidth()) {
                Text(settings.accountName.ifBlank { settings.playerName.ifBlank { stringResource(R.string.not_logged_in) } }, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                Text(if (settings.loggedIn) stringResource(R.string.logged_in_as, settings.accountName) else stringResource(R.string.product_sync_local), color = palette.textSecondary)
                TextButton(onClick = { if (settings.loggedIn) sheet = "logout" else vm.openLogin() }) { Text(stringResource(if (settings.loggedIn) R.string.log_out else R.string.log_in)) }
                if (settings.loggedIn) TextButton(onClick = {
                    val count = vm.gameSaves.importDeviceGames()
                    vm.syncGames()
                    android.widget.Toast.makeText(context, context.getString(R.string.product_imported, count), android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.product_import_device)) }
            } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatPill(stringResource(R.string.wins), (if (settings.loggedIn) settings.accWins else settings.wins).toString(), valueColor = palette.success)
                StatPill(stringResource(R.string.losses), (if (settings.loggedIn) settings.accLosses else settings.losses).toString())
                StatPill(stringResource(R.string.best_solo), settings.bestSolo.toString(), valueColor = palette.gold)
            } }
            item { GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.product_progress), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatPill(stringResource(R.string.level_label), Economy.level(settings.score).toString())
                    StatPill(stringResource(R.string.score_label), settings.score.toString())
                    StatPill(stringResource(R.string.coins_label), settings.coins.toString(), valueColor = palette.gold)
                }
                TextButton(onClick = { sheet = "shop" }) { Text(stringResource(R.string.coin_shop)) }
            } }
            item { ActionCard(stringResource(R.string.settings), stringResource(R.string.product_settings_hint)) { sheet = "settings" } }
            item { ActionCard(stringResource(R.string.report_admin), stringResource(R.string.product_help_hint)) { sheet = "report" } }
        }
    }
    when (sheet) {
        "settings" -> SettingsSheet(settings, onDismiss = { sheet = "" }) { vm.updateSettings(it) }
        "shop" -> ShopSheet(vm) { sheet = "" }; "report" -> ReportSheet(vm) { sheet = "" }
        "logout" -> AlertDialog(onDismissRequest = { sheet = "" }, title = { Text(stringResource(R.string.log_out)) },
            text = { Text(stringResource(R.string.product_logout_hint)) },
            confirmButton = { TextButton(onClick = { vm.logout(); sheet = "" }) { Text(stringResource(R.string.log_out)) } },
            dismissButton = { TextButton(onClick = { sheet = "" }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = palette.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ArrowForward, null, tint = palette.textSecondary)
        }
    }
}
