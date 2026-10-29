package com.duel2048.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.duel2048.shared.social.WordGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duel2048.app.MainViewModel
import com.duel2048.app.MiniUi
import com.duel2048.app.R
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.protocol.SavedGameType
import com.duel2048.shared.social.Puzzles

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PuzzleAnswerControls(vm: MainViewModel, state: MiniUi) {
    val palette = LocalPalette.current
    val enabled = !state.submitting && !state.answered
    var selectedAnswer by rememberSaveable(state.saveId) { mutableStateOf("") }
    val gridGame = state.game == SavedGameType.WORD_PUZZLE
    val wordGame = state.game == SavedGameType.WORD || gridGame
    val expectedLetters = if (gridGame) 8 else if (state.grid.size == 1) state.grid.first().length else state.difficulty.toIntOrNull() ?: 4
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (wordGame) {
            val assembled = (if (gridGame) WordGrid.selectedWord(state.grid, state.selectedLetters) else Puzzles.selectedWord(state.grid, state.selectedLetters)).orEmpty()
            Text(stringResource(if (gridGame) R.string.word_grid_help else R.string.puzzle_letters_help), color = palette.textSecondary)
            Text(if (gridGame) assembled.ifEmpty { "—" } else (assembled.toList().map { it.toString() } + List((expectedLetters - assembled.length).coerceAtLeast(0)) { "_" }).joinToString(" "),
                color = palette.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (gridGame) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.grid.forEachIndexed { row, letters ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            letters.forEachIndexed { col, letter ->
                                val index = row * state.grid.size + col
                                val order = state.selectedLetters.indexOf(index)
                                val last = state.selectedLetters.lastOrNull() == index
                                val canSelect = enabled && (last || WordGrid.selectedWord(state.grid, state.selectedLetters + index) != null)
                                val shape = RoundedCornerShape(6.dp)
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(shape)
                                    .background(if (order >= 0) palette.accent.copy(alpha = 0.28f) else palette.surface)
                                    .border(if (last) 2.dp else 1.dp, if (order >= 0) palette.accent else palette.surfaceBorder, shape)
                                    .clickable(enabled = canSelect) { if (last) vm.undoMiniLetter() else vm.selectMiniLetter(index) }
                                    .semantics { contentDescription = "${letter}, row ${row + 1}, column ${col + 1}" + if (order >= 0) ", selected ${order + 1}" else "" },
                                    contentAlignment = Alignment.Center) {
                                    Text(letter.toString(), color = if (canSelect || order >= 0) palette.textPrimary else palette.textSecondary,
                                        fontSize = if (state.grid.size == 8) 17.sp else 22.sp, fontWeight = FontWeight.Bold)
                                    if (order >= 0) Text("${order + 1}", color = palette.accent, fontSize = 9.sp,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp))
                                }
                            }
                        }
                    }
                }
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.grid.joinToString("").forEachIndexed { index, letter ->
                    val used = index in state.selectedLetters
                    OutlinedButton(onClick = { vm.selectMiniLetter(index) },
                        enabled = enabled && !used && state.selectedLetters.size < expectedLetters,
                        modifier = Modifier.padding(vertical = 4.dp).size(52.dp), contentPadding = PaddingValues(0.dp)) {
                        Text(letter.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.undoMiniLetter() }, enabled = enabled && state.selectedLetters.isNotEmpty()) {
                    Text(stringResource(R.string.puzzle_undo))
                }
                TextButton(onClick = { vm.undoMiniLetter(clear = true) }, enabled = enabled && state.selectedLetters.isNotEmpty()) {
                    Text(stringResource(R.string.puzzle_clear))
                }
            }
        } else {
            Text(stringResource(R.string.puzzle_choose_answer), color = palette.textSecondary)
            state.choices.forEach { answer ->
                OutlinedButton(onClick = { selectedAnswer = answer }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (answer == selectedAnswer) palette.accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (answer == selectedAnswer) palette.accent else palette.textPrimary)) {
                    RadioButton(selected = answer == selectedAnswer, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(answer, fontSize = 20.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        if (state.invalidWord) Text(stringResource(R.string.word_grid_invalid), color = palette.danger)
        if (state.answerFailed) Text(stringResource(R.string.puzzle_submit_failed), color = palette.danger)
        if (state.answered) Text(stringResource(R.string.puzzle_wrong_wait), color = palette.gold)
        else Button(onClick = { vm.submitMini(selectedAnswer) }, modifier = Modifier.fillMaxWidth(),
            enabled = enabled && if (gridGame) state.selectedLetters.size in 3..8 else if (wordGame) state.selectedLetters.size == expectedLetters else selectedAnswer in state.choices) {
            Text(stringResource(if (state.submitting) R.string.puzzle_sending else R.string.submit_answer))
        }
        if (state.guided && !gridGame) TextButton(onClick = { vm.miniHint() }, enabled = enabled) {
            Text(stringResource(R.string.hint))
        }
    }
}
