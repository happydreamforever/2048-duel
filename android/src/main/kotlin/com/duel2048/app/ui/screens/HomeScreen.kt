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
    PLAY(R.string.product_play, Icons.Default.Home),
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
                        ProductTab.PLAY -> vm.goHome()
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
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val update by AppUpdater.status.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val context = LocalContext.current
    LaunchedEffect(settings.serverUrl) { AppUpdater.checkAndInstall(context, settings.serverUrl) }

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
            Text(when (game) { HubGame.TILES -> "2048"; HubGame.CUBE -> "3×3"; HubGame.MATH -> "±"; HubGame.WORD -> "Aa"; HubGame.WORD_PUZZLE -> "ABC" },
                color = androidx.compose.ui.graphics.Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(gameTitle(game), color = palette.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(when (game) {
                HubGame.TILES -> R.string.product_tiles_description; HubGame.CUBE -> R.string.product_cube_description
                HubGame.MATH -> R.string.product_math_description; HubGame.WORD -> R.string.product_word_description; HubGame.WORD_PUZZLE -> R.string.word_grid_description
            }), color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ArrowForward, null, tint = palette.textSecondary)
    }
}

@Composable
internal fun gameTitle(game: HubGame): String = savedGameTitle(when (game) {
    HubGame.TILES -> SavedGameType.TILES; HubGame.CUBE -> SavedGameType.CUBE
    HubGame.MATH -> SavedGameType.MATH; HubGame.WORD -> SavedGameType.WORD; HubGame.WORD_PUZZLE -> SavedGameType.WORD_PUZZLE
})

@Composable
private fun savedGameTitle(game: SavedGameType): String = when (game) {
    SavedGameType.TILES -> "2048"; SavedGameType.CUBE -> stringResource(R.string.product_cube)
    SavedGameType.MATH -> stringResource(R.string.product_math); SavedGameType.WORD -> stringResource(R.string.product_word)
    SavedGameType.WORD_PUZZLE -> stringResource(R.string.word_grid_title)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedGamesScreen(vm: MainViewModel) { LaunchedEffect(Unit) { vm.goHome() } }

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
