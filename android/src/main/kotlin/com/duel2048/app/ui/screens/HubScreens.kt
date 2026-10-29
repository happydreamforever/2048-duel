package com.duel2048.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.duel2048.app.ui.components.ShimmerTitle
import com.duel2048.app.ui.theme.LocalLowEffects
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.duel2048.shared.social.ChatPhrases
import com.duel2048.shared.social.Economy
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GameModesScreen(vm: MainViewModel, game: HubGame) {
    val palette=LocalPalette.current
    BackHandler { vm.goHome() }
    var difficulty by remember(game) { mutableStateOf(if(game in listOf(HubGame.WORD,HubGame.WORD_PUZZLE)) "4" else if(game==HubGame.MATH) "medium" else "normal") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick={vm.goHome()}) { Icon(Icons.Default.ArrowBack,null);Spacer(Modifier.width(6.dp));Text(stringResource(R.string.product_play)) }
        ShimmerTitle(gameTitle(game), fontSize = 20.sp, letterSpacing = 0.sp)
        Text(stringResource(R.string.product_mode_subtitle),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.difficulty),style=MaterialTheme.typography.titleSmall,color=palette.textPrimary)
            val options=when(game) {
                HubGame.WORD_PUZZLE -> com.duel2048.shared.social.WordGrid.sizes.map(Int::toString)
                HubGame.WORD -> com.duel2048.shared.social.Puzzles.wordLengths.map(Int::toString)
                HubGame.MATH -> listOf("easy","medium","hard","very_hard")
                else -> listOf("easy","normal","hard")
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                options.forEach { id -> FilterChip(selected=id==difficulty,onClick={difficulty=id},label={Text(if(game==HubGame.WORD_PUZZLE) "$id × $id" else difficultyLabel(id),style=MaterialTheme.typography.labelMedium)}) }
            }
        }
        ModeChoice(stringResource(R.string.mode_pvp),stringResource(R.string.mode_pvp_sub),Icons.Default.Person,stringResource(R.string.app_entry_coins,10),true) { vm.startHub(game,HubMode.PVP,difficulty) }
        ModeChoice(stringResource(R.string.challenge_specific),stringResource(R.string.challenge_specific_hint),Icons.Default.Person,stringResource(R.string.app_entry_coins,10),colors=game.colors) { vm.openChallenges(game,difficulty) }
        ModeChoice(stringResource(R.string.mode_bot),stringResource(R.string.mode_bot_sub),Icons.Default.PlayArrow,stringResource(R.string.app_entry_coins,5),colors=listOf(Color(0xFF4A6FA5),Color(0xFF1B3A5C))) { vm.startHub(game,HubMode.BOT,difficulty) }
        ModeChoice(stringResource(R.string.mode_train),stringResource(R.string.mode_train_sub),Icons.Default.Refresh,stringResource(R.string.app_free),colors=listOf(Color(0xFF1F9D6B),Color(0xFF0E6B8A))) { vm.startHub(game,HubMode.TRAIN,difficulty) }
        ModeChoice(stringResource(R.string.mode_watch),stringResource(R.string.mode_watch_sub),Icons.Default.Star,stringResource(R.string.app_live),colors=listOf(Color(0xFF5C4A7A),Color(0xFF2A2140))) { vm.startHub(game,HubMode.WATCH,difficulty) }
        if(game==HubGame.TILES) TextButton(onClick={vm.openSolo()},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.product_solo)) }
        Text(stringResource(R.string.app_reward_coins,25),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
    }
}

@Composable
private fun ModeChoice(title:String,subtitle:String,icon:ImageVector,badge:String,primary:Boolean=false,colors:List<Color>?=null,onClick:()->Unit) {
    val palette = LocalPalette.current
    val gradient = colors ?: listOf(palette.accent2, palette.accent)
    val shape = RoundedCornerShape(18.dp)
    val lowEffects = LocalLowEffects.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !lowEffects) 0.97f else 1f, spring(), label = "mode-press")
    Row(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
        .drawBehind {
            if (!lowEffects) {
                for (i in 1..3) {
                    val pad = i * 3.dp.toPx()
                    drawRoundRect(gradient.last().copy(alpha = (if (primary) 0.16f else 0.09f) / i),
                        topLeft = Offset(-pad, -pad), size = Size(size.width + 2 * pad, size.height + 2 * pad),
                        cornerRadius = CornerRadius(18.dp.toPx() + pad))
                }
            }
        }
        .clip(shape).background(Brush.horizontalGradient(gradient))
        .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f))
        }
        Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.24f)).padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
fun WatchScreen(vm: MainViewModel) {
    BackHandler { vm.openCommunity() }
    val palette = LocalPalette.current
    val live by vm.liveGames.collectAsStateWithLifecycle()
    val focus by vm.watchFocus.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshLive()
            delay(2000)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(stringResource(R.string.mode_watch), style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        if (live.isEmpty()) {
            Text(stringResource(R.string.watch_empty), color = palette.textSecondary)
        } else {
            val chosen = focus?.let { id -> live.find { it.id == id } } ?: live.first()
            val settings by vm.settings.collectAsStateWithLifecycle()
            var frame by remember(chosen.id) { mutableStateOf<com.duel2048.shared.social.WatchFrame?>(null) }
            var watchStatus by remember(chosen.id) { mutableStateOf("Connecting to live stream…") }
            LaunchedEffect(chosen.id,settings.serverUrl) {
                while(true) {
                    try {
                        com.duel2048.app.net.WatchClient().frames(settings.serverUrl,chosen.id).collect {
                            frame=it; watchStatus="Live · server signal #${it.sequence} · ${it.event}"
                        }
                    } catch(e:kotlinx.coroutines.CancellationException) { throw e }
                    catch(e:Exception) { watchStatus="Connection interrupted. Reconnecting…" }
                    delay(2000)
                }
            }
            val shown = frame?.room ?: chosen
            GlassCard(Modifier.fillMaxWidth()) {
                if(shown.demo) Text("DEMO ROOM · simulated players · no entry fee or rewards",color=palette.gold)
                Text(watchStatus,color=palette.accent)
                Text(shown.game.uppercase(), color = palette.accent, fontWeight = FontWeight.Black)
                Text("${shown.playerA}  ${shown.scoreA}", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${shown.playerB}  ${shown.scoreB}", color = palette.textSecondary, fontSize = 18.sp)
            }
            frame?.let { snapshot ->
                if(snapshot.grid.isNotEmpty()) {
                    Text("${snapshot.seconds}s · ${snapshot.serverTime}",color=palette.textSecondary)
                    snapshot.grid.forEachIndexed { row, letters ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            letters.forEachIndexed { col,letter ->
                                Box(Modifier.weight(1f).aspectRatio(1f).background(if(row*snapshot.grid.size+col in snapshot.selected) palette.accent.copy(alpha=0.35f) else palette.surface),contentAlignment=Alignment.Center) {
                                    Text(letter.toString(),color=palette.textPrimary,fontWeight=FontWeight.Bold,fontSize=18.sp)
                                }
                            }
                        }
                    }
                }
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
        TextButton(onClick = { vm.openCommunity() }) { Text(stringResource(R.string.back), color = palette.accent) }
    }
}

@Composable
fun MiniPlayScreen(vm: MainViewModel) {
    BackHandler { vm.closeMini() }
    val palette = LocalPalette.current
    val mini by vm.mini.collectAsStateWithLifecycle()
    val state = mini
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.loading), color = palette.textSecondary)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(gameTitle(if (state.game == com.duel2048.shared.protocol.SavedGameType.WORD_PUZZLE) HubGame.WORD_PUZZLE else if (state.game == com.duel2048.shared.protocol.SavedGameType.WORD) HubGame.WORD else HubGame.MATH), color = palette.accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(stringResource(R.string.vs_name, state.opponent), color = palette.textSecondary)
        Text("${state.secondsLeft}s", color = palette.gold, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(state.prompt, color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = if (state.grid.isEmpty()) 22.sp else 18.sp)
        if (state.game == com.duel2048.shared.protocol.SavedGameType.WORD_PUZZLE) {
            Text(stringResource(R.string.word_grid_score, state.score, state.opponentScore), color = palette.gold, fontWeight = FontWeight.Bold)
            if (state.foundWords.isNotEmpty()) Text(stringResource(R.string.word_grid_found, state.foundWords.joinToString(", ")), color = palette.success)
        }
        if (state.hint.isNotBlank()) Text(stringResource(R.string.hint_line, state.hint), color = palette.accent)
        if (state.status == "playing") {
            PuzzleAnswerControls(vm, state)
        } else if (state.status == "waiting") {
            Text(stringResource(if(state.opponent!="waiting") R.string.challenge_starting else R.string.product_waiting), color = palette.textSecondary)
        } else {
            Text(
                if (state.game == com.duel2048.shared.protocol.SavedGameType.WORD_PUZZLE && state.mode != "train" && state.score == state.opponentScore) stringResource(R.string.word_grid_draw) else if (state.won) stringResource(R.string.you_win) else stringResource(R.string.mini_game_over),
                color = if (state.won) palette.success else palette.danger,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            if (state.solution.isNotBlank()) Text(stringResource(R.string.solution_line, state.solution), color = palette.gold)
            NeonButton(stringResource(R.string.play_again), modifier = Modifier.fillMaxWidth()) { vm.playMiniAgain() }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatSheet(vm: MainViewModel, room: String, onDismiss: () -> Unit) {
    val palette=LocalPalette.current
    val lines by vm.chat.collectAsStateWithLifecycle()
    val error by vm.chatError.collectAsStateWithLifecycle()
    val listState=rememberLazyListState()
    LaunchedEffect(room) { while(true) { vm.refreshChat(room);delay(2000) } }
    LaunchedEffect(lines.size) { if(lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=palette.bgTop) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pvp_chat_title),style=MaterialTheme.typography.titleMedium,color=palette.textPrimary)
            Text(stringResource(R.string.pvp_chat_hint),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
            if(lines.isEmpty()) Text(stringResource(R.string.pvp_chat_empty),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
            else LazyColumn(state=listState,modifier=Modifier.fillMaxWidth().heightIn(max=180.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(lines) { line -> Surface(color=palette.surface,shape=RoundedCornerShape(10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(line.from,style=MaterialTheme.typography.labelSmall,color=palette.accent)
                        Text(line.text,style=MaterialTheme.typography.bodyMedium,color=palette.textPrimary)
                    }
                } }
            }
            if(error) Text(stringResource(R.string.pvp_chat_failed),style=MaterialTheme.typography.bodySmall,color=palette.danger)
            Column(Modifier.heightIn(max=260.dp).verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    ChatPhrases.all.forEach { phrase -> AssistChip(onClick={vm.sendChat(room,phrase)},label={Text(phrase,style=MaterialTheme.typography.labelMedium)}) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val purchaseMessage by vm.purchaseMessage.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.bgTop) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.coin_shop), color = palette.gold, fontWeight = FontWeight.Black)
            Text("Choose a coin pack to request a purchase. An admin confirms payment before coins are added.", color = palette.textSecondary)
            if(purchaseMessage.isNotBlank()) Text(purchaseMessage,color=palette.accent)
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
    val palette=LocalPalette.current
    val notes by vm.notes.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=palette.bgTop) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.app_notifications),style=MaterialTheme.typography.titleMedium,color=palette.textPrimary)
            if(notes.isEmpty()) Text(stringResource(R.string.no_notes),style=MaterialTheme.typography.bodyMedium,color=palette.textSecondary)
            LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(notes.reversed(),key={it.id}) { note -> GlassCard(Modifier.fillMaxWidth()) {
                    Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(note.at)),style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text(note.text,style=MaterialTheme.typography.bodyMedium,color=palette.textPrimary)
                } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val palette=LocalPalette.current
    val state by vm.reportState.collectAsStateWithLifecycle()
    val text by vm.reportDraft.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=palette.bgTop) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.app_contact_support),style=MaterialTheme.typography.titleMedium,color=palette.textPrimary)
            Text(stringResource(R.string.app_support_hint),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
            if(state.sent) {
                Text(stringResource(R.string.app_support_sent),style=MaterialTheme.typography.bodyMedium,color=palette.success)
                Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.ok)) }
            } else {
                OutlinedTextField(value=text,onValueChange=vm::updateReportDraft,enabled=!state.busy,
                    label={Text(stringResource(R.string.app_support_message))},supportingText={Text("${text.length}/400")},
                    minLines=4,maxLines=7,modifier=Modifier.fillMaxWidth(),colors=duelFieldColors())
                if(state.error) Text(stringResource(R.string.app_support_failed),style=MaterialTheme.typography.bodySmall,color=palette.danger)
                if(state.signInRequired) Text(stringResource(R.string.app_support_sign_in),style=MaterialTheme.typography.bodySmall,color=palette.gold)
                Button(onClick={vm.sendReport(text)},enabled=!state.busy&&text.trim().length>=3,modifier=Modifier.fillMaxWidth()) {
                    if(state.busy) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp) else Text(stringResource(R.string.send_report))
                }
                if(state.signInRequired) TextButton(onClick={onDismiss();vm.openLogin()},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_in)) }
            }
        }
    }
}

@Composable
internal fun difficultyLabel(id: String): String = when (id) {
    "easy" -> stringResource(R.string.easy)
    "medium", "normal" -> stringResource(R.string.medium)
    "hard" -> stringResource(R.string.hard)
    "very_hard" -> stringResource(R.string.very_hard)
    "3", "4", "5", "6", "7", "8" -> stringResource(R.string.puzzle_word_length, id)
    else -> id
}
