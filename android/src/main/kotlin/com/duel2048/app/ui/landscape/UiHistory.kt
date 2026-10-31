@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.duel2048.app.ui.landscape

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.protocol.Protocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
internal data class UiResult(
    val id: String,
    val game: String,
    val opponent: String,
    val outcome: String,
    val score: Int,
    val at: Long
)

private val serializer = ListSerializer(UiResult.serializer())

@Composable
private fun historyPrefs() = LocalContext.current.getSharedPreferences("landscape_results", 0)

private fun historyKey(vm: MainViewModel) =
    vm.settings.value.serverUrl + "|" + vm.settings.value.accountName.ifBlank { "offline" }

private fun readHistory(prefs: android.content.SharedPreferences, key: String): List<UiResult> =
    runCatching { Protocol.json.decodeFromString(serializer, prefs.getString(key, "[]") ?: "[]") }
        .getOrDefault(emptyList())

/** UI-only observation; never sends a move, charges a wallet or changes competition state. */
@Composable
fun RememberResultHistory(vm: MainViewModel) {
    val prefs = historyPrefs()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val duel by session.state.collectAsStateWithLifecycle()
    val cube by vm.cubeUiState.collectAsStateWithLifecycle()
    val mini by vm.mini.collectAsStateWithLifecycle()
    val identity = settings.serverUrl + "|" + settings.accountName.ifBlank { "offline" }
    fun add(r: UiResult) {
        val old = readHistory(prefs, identity)
        if (old.none { it.id == r.id })
            prefs
                .edit()
                .putString(
                    identity, Protocol.json.encodeToString(serializer, (listOf(r) + old).take(50)))
                .apply()
    }
    LaunchedEffect(duel.result) {
        if (duel.phase == DuelPhase.OVER && duel.result != null)
            add(
                UiResult(
                    "tiles:${duel.matchId}",
                    "2048",
                    duel.opponent.info?.name.orEmpty(),
                    if (duel.draw) "Draw" else if (duel.won) "Won" else "Lost",
                    duel.me.score,
                    System.currentTimeMillis()))
    }
    LaunchedEffect(cube.result) {
        if (cube.phase == DuelPhase.OVER && cube.result != null)
            add(
                UiResult(
                    "cube:${cube.matchId}:${cube.scrambleNonce}",
                    "Cube",
                    cube.opponent?.name.orEmpty(),
                    if (cube.draw) "Draw" else if (cube.won) "Won" else "Lost",
                    cube.myMoves,
                    System.currentTimeMillis()))
    }
    LaunchedEffect(mini?.saveId, mini?.status) {
        mini
            ?.takeIf { it.status == "over" }
            ?.let { s ->
                add(
                    UiResult(
                        s.saveId,
                        s.title,
                        s.opponent,
                        if (s.won) "Won"
                        else if (s.game == com.duel2048.shared.protocol.SavedGameType.WORD_PUZZLE &&
                            s.mode != "train" &&
                            s.score == s.opponentScore)
                            "Draw"
                        else "Finished",
                        s.score,
                        System.currentTimeMillis()))
            }
    }
}

@Composable
fun HistoryList(vm: MainViewModel) {
    val prefs = historyPrefs()
    val key = historyKey(vm)
    val rows = remember(key) { readHistory(prefs, key) }
    val p = LocalPalette.current
    if (rows.isEmpty()) {
        EmptyState(
            "No results on this device yet",
            "Completed matches viewed on this device will appear here.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recorded on this device · current account and server", color = p.textSecondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { it.id }) { r ->
                NeonPane(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.game + " · " + r.opponent, color = p.textPrimary)
                        Text(r.outcome, color = if (r.outcome == "Won") p.success else p.gold)
                    }
                    Text(
                        (if (r.game == "Cube") "Moves" else "Score") +
                            ": ${r.score} · " +
                            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(r.at)),
                        color = p.textSecondary)
                }
            }
        }
    }
}
