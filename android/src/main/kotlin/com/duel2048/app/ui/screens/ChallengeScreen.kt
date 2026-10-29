package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.*
import com.duel2048.app.R
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.components.ShimmerTitle
import com.duel2048.app.ui.theme.LocalPalette

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(vm:MainViewModel,screen:Screen.Challenges) {
    val palette=LocalPalette.current
    val focus=LocalFocusManager.current
    val invitations by vm.challenges.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.challengeBusy.collectAsStateWithLifecycle()
    val error by vm.challengeError.collectAsStateWithLifecycle()
    val joining by vm.joiningChallenge.collectAsStateWithLifecycle()
    var opponent by rememberSaveable(screen.game,screen.difficulty,screen.opponent) { mutableStateOf(screen.opponent) }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { vm.refreshPlayers();while(true) { clock=System.currentTimeMillis();kotlinx.coroutines.delay(1000) } }
    BackHandler { if(joining!=null) vm.respondChallenge(checkNotNull(joining),"cancel") else vm.goHome() }
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick={if(joining!=null) vm.respondChallenge(checkNotNull(joining),"cancel") else vm.goHome()},enabled=!busy) {
                Text(stringResource(if(joining!=null) R.string.challenge_cancel else R.string.back))
            }
            ShimmerTitle(stringResource(R.string.challenge_title),fontSize=20.sp,letterSpacing=0.sp)
            Text(stringResource(R.string.challenge_your_name,settings.accountName),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
            Text(stringResource(R.string.challenge_rules),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
        }
        if(error!=null) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(error.orEmpty(),color=palette.danger,style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={vm.clearChallengeError();vm.refreshChallenges()}) { Text(stringResource(R.string.challenge_refresh)) }
            }
        }
        if(screen.game==null && joining==null) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.challenge_specific),style=MaterialTheme.typography.titleSmall,color=palette.accent)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    HubGame.entries.forEach { game -> FilterChip(selected=false,onClick={vm.openChallenges(game,when(game){HubGame.MATH->"medium";HubGame.WORD,HubGame.WORD_PUZZLE->"4";else->"normal"})},label={Text(gameTitle(game))}) }
                }
            }
        }
        if(screen.game!=null && joining==null) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(gameTitle(screen.game),style=MaterialTheme.typography.titleMedium,color=palette.accent)
                Text(stringResource(R.string.challenge_selected_level,if(screen.game==HubGame.WORD_PUZZLE) "${screen.difficulty} × ${screen.difficulty}" else difficultyLabel(screen.difficulty)),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value=opponent,onValueChange={opponent=it.take(16)},singleLine=true,enabled=!busy,
                    modifier=Modifier.fillMaxWidth(),label={Text(stringResource(R.string.challenge_player_name))},colors=duelFieldColors())
                Text(stringResource(R.string.challenge_name_hint),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
                players.filter { it.name!=settings.accountName && opponent.isNotBlank() && it.name.contains(opponent,ignoreCase=true) }.take(4).forEach { player ->
                    TextButton(onClick={opponent=player.name},enabled=!busy) { Text(player.name) }
                }
                Spacer(Modifier.height(10.dp))
                NeonButton(stringResource(if(busy) R.string.puzzle_sending else R.string.challenge_send),modifier=Modifier.fillMaxWidth(),
                    enabled=!busy&&opponent.trim().length>=3&& !opponent.equals(settings.accountName,ignoreCase=true)) {
                    focus.clearFocus()
                    vm.createChallenge(screen.game,screen.difficulty,opponent)
                }
            }
        }
        if(invitations.isEmpty()) item { Text(stringResource(R.string.challenge_empty),color=palette.textSecondary,style=MaterialTheme.typography.bodyMedium) }
        items(invitations.sortedByDescending { it.expiresAt },key={it.id}) { invite ->
            val other=if(invite.incoming) invite.from else invite.to
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(if(invite.incoming) R.string.challenge_from else R.string.challenge_to,other),style=MaterialTheme.typography.titleMedium,color=palette.textPrimary)
                Text(gameTitle(gameFromKey(invite.game))+" · "+(if(invite.game=="word_puzzle") "${invite.difficulty} × ${invite.difficulty}" else difficultyLabel(invite.difficulty)),style=MaterialTheme.typography.bodySmall,color=palette.accent)
                Text(stringResource(when(invite.status) {
                    "pending"->R.string.challenge_pending;"accepted"->R.string.challenge_accepted;"playing"->R.string.challenge_playing
                    "declined"->R.string.challenge_declined;"cancelled"->R.string.challenge_cancelled;"expired"->R.string.challenge_expired
                    else->R.string.challenge_completed
                }),style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
                if(invite.status in listOf("pending","accepted")) Text(stringResource(R.string.challenge_expires,((invite.expiresAt-clock)/1000).coerceAtLeast(0).toInt()),style=MaterialTheme.typography.labelSmall,color=palette.gold)
                if(invite.youReady && invite.status=="accepted") Text(stringResource(R.string.challenge_waiting_player,other),style=MaterialTheme.typography.bodySmall,color=palette.gold)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(invite.status=="pending" && invite.incoming) {
                        Button(onClick={vm.respondChallenge(invite.id,"accept")},enabled=!busy&&joining==null) { Text(stringResource(R.string.challenge_accept)) }
                        TextButton(onClick={vm.respondChallenge(invite.id,"decline")},enabled=!busy&&joining==null) { Text(stringResource(R.string.challenge_decline)) }
                    }
                    if(invite.status in listOf("accepted","playing")) Button(onClick={vm.joinChallenge(invite)},enabled=!busy&&joining==null) {
                        Text(stringResource(if(invite.status=="playing") R.string.challenge_return else R.string.challenge_join))
                    }
                    if(invite.status in listOf("pending","accepted") && (!invite.incoming||invite.status=="accepted")) TextButton(onClick={vm.respondChallenge(invite.id,"cancel")},enabled=!busy&&(joining==null||joining==invite.id)) { Text(stringResource(R.string.challenge_cancel)) }
                    if(invite.status=="completed" && joining==null) TextButton(onClick={vm.openChallenges(gameFromKey(invite.game),invite.difficulty,other)},enabled=!busy) { Text(stringResource(R.string.challenge_rematch)) }
                }
            }
        }
    }
}
