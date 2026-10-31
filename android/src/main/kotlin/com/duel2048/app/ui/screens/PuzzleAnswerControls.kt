package com.duel2048.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.duel2048.app.*
import com.duel2048.app.ui.landscape.ArcadeAction
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.protocol.SavedGameType
import com.duel2048.shared.social.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PuzzleAnswerControls(vm: MainViewModel, state: MiniUi) {
    val p = LocalPalette.current
    val enabled = !state.submitting && !state.answered
    var selectedAnswer by rememberSaveable(state.saveId) { mutableStateOf("") }
    val gridGame = state.game == SavedGameType.WORD_PUZZLE
    val wordGame = state.game == SavedGameType.WORD || gridGame
    val expected =
        if (gridGame) 8
        else if (state.grid.size == 1) state.grid.first().length
        else state.difficulty.toIntOrNull() ?: 4
    val assembled =
        if (wordGame)
            (if (gridGame) WordGrid.selectedWord(state.grid, state.selectedLetters)
                else Puzzles.selectedWord(state.grid, state.selectedLetters))
                .orEmpty()
        else ""
    val submit: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.invalidWord)
                Text(
                    "Use a new valid word from neighboring letters.",
                    color = p.danger,
                    style = MaterialTheme.typography.bodySmall)
            if (state.answerFailed)
                Text(
                    "Submission failed. Try again.",
                    color = p.danger,
                    style = MaterialTheme.typography.bodySmall)
            if (state.answered)
                Text(
                    "Answer submitted. Waiting for the round to finish.",
                    color = p.gold,
                    style = MaterialTheme.typography.bodySmall)
            else
                ArcadeAction(
                    if (state.submitting) "Sending…" else "Submit Answer",
                    Modifier.fillMaxWidth(),
                    enabled =
                        enabled &&
                            if (gridGame) state.selectedLetters.size in 3..8
                            else if (wordGame) state.selectedLetters.size == expected
                            else selectedAnswer in state.choices) {
                        vm.submitMini(selectedAnswer)
                    }
            if (state.guided && !gridGame)
                TextButton(onClick = { vm.miniHint() }, enabled = enabled) { Text("Hint") }
        }
    }
    if (gridGame) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.weight(.65f).fillMaxHeight()) {
                val side = minOf(maxWidth, maxHeight)
                Column(Modifier.size(side), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    state.grid.forEachIndexed { r, letters ->
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                letters.forEachIndexed { c, letter ->
                                    val index = r * state.grid.size + c
                                    val order = state.selectedLetters.indexOf(index)
                                    val last = state.selectedLetters.lastOrNull() == index
                                    val can =
                                        enabled &&
                                            (last ||
                                                WordGrid.selectedWord(
                                                    state.grid, state.selectedLetters + index) !=
                                                    null)
                                    val shape = RoundedCornerShape(6.dp)
                                    Box(
                                        Modifier.weight(1f)
                                            .fillMaxHeight()
                                            .clip(shape)
                                            .background(
                                                if (order >= 0) p.accent.copy(alpha = .28f)
                                                else p.surface)
                                            .border(
                                                if (last) 2.dp else 1.dp,
                                                if (order >= 0) p.accent else p.surfaceBorder,
                                                shape)
                                            .clickable(enabled = can) {
                                                if (last) vm.undoMiniLetter()
                                                else vm.selectMiniLetter(index)
                                            }
                                            .semantics {
                                                contentDescription =
                                                    "$letter, row ${r+1}, column ${c+1}" +
                                                        if (order >= 0) ", selected ${order+1}"
                                                        else ""
                                            },
                                        contentAlignment = Alignment.Center) {
                                            Text(
                                                letter.toString(),
                                                fontSize =
                                                    if (state.grid.size == 8) 14.sp else 20.sp,
                                                color =
                                                    if (can || order >= 0) p.textPrimary
                                                    else p.textSecondary,
                                                fontWeight = FontWeight.Bold)
                                            if (order >= 0)
                                                Text(
                                                    "${order+1}",
                                                    fontSize = 8.sp,
                                                    color = p.accent,
                                                    modifier =
                                                        Modifier.align(Alignment.TopEnd)
                                                            .padding(2.dp))
                                        }
                                }
                            }
                    }
                }
            }
            Column(
                Modifier.weight(.35f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        assembled.ifBlank { "—" },
                        color = p.accent,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Horizontal or vertical only",
                        color = p.textSecondary,
                        style = MaterialTheme.typography.labelSmall)
                    Row {
                        TextButton(
                            onClick = { vm.undoMiniLetter() },
                            enabled = enabled && state.selectedLetters.isNotEmpty()) {
                                Text("Undo")
                            }
                        TextButton(
                            onClick = { vm.undoMiniLetter(true) },
                            enabled = enabled && state.selectedLetters.isNotEmpty()) {
                                Text("Clear")
                            }
                    }
                    submit()
                    Text("Your words", color = p.gold, style = MaterialTheme.typography.labelMedium)
                    state.foundWords.forEach {
                        Text(
                            it, color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
        }
    } else
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wordGame) {
                    Text(
                        (assembled.toList().map { it.toString() } +
                                List((expected - assembled.length).coerceAtLeast(0)) { "_" })
                            .joinToString(" "),
                        fontSize = 24.sp,
                        color = p.accent,
                        fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.grid.joinToString("").forEachIndexed { i, ch ->
                            OutlinedButton(
                                onClick = { vm.selectMiniLetter(i) },
                                enabled =
                                    enabled &&
                                        i !in state.selectedLetters &&
                                        state.selectedLetters.size < expected,
                                modifier = Modifier.size(52.dp),
                                contentPadding = PaddingValues(0.dp)) {
                                    Text(ch.toString(), fontSize = 20.sp)
                                }
                        }
                    }
                    Row {
                        TextButton(
                            onClick = { vm.undoMiniLetter() },
                            enabled = enabled && state.selectedLetters.isNotEmpty()) {
                                Text("Undo")
                            }
                        TextButton(
                            onClick = { vm.undoMiniLetter(true) },
                            enabled = enabled && state.selectedLetters.isNotEmpty()) {
                                Text("Clear")
                            }
                    }
                } else {
                    state.choices.chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { answer ->
                                    OutlinedButton(
                                        onClick = { selectedAnswer = answer },
                                        enabled = enabled,
                                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                        colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                containerColor =
                                                    if (selectedAnswer == answer)
                                                        p.accent.copy(alpha = .24f)
                                                    else p.surface,
                                                contentColor = p.textPrimary)) {
                                            RadioButton(selectedAnswer == answer, null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(answer, fontSize = 18.sp, maxLines = 1)
                                        }
                                }
                            }
                    }
                }
                submit()
            }
}
