package com.duel2048.app.ui.screens

import androidx.compose.foundation.layout.*
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
    val wordGame = state.game == SavedGameType.WORD
    val expectedLetters = if (state.grid.size == 1) state.grid.first().length else state.difficulty.toIntOrNull() ?: 4
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (wordGame) {
            val assembled = Puzzles.selectedWord(state.grid, state.selectedLetters).orEmpty()
            Text(stringResource(R.string.puzzle_letters_help), color = palette.textSecondary)
            Text((assembled.toList().map { it.toString() } + List((expectedLetters - assembled.length).coerceAtLeast(0)) { "_" }).joinToString(" "),
                color = palette.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        if (state.answerFailed) Text(stringResource(R.string.puzzle_submit_failed), color = palette.danger)
        if (state.answered) Text(stringResource(R.string.puzzle_wrong_wait), color = palette.gold)
        else Button(onClick = { vm.submitMini(selectedAnswer) }, modifier = Modifier.fillMaxWidth(),
            enabled = enabled && if (wordGame) state.selectedLetters.size == expectedLetters else selectedAnswer in state.choices) {
            Text(stringResource(if (state.submitting) R.string.puzzle_sending else R.string.submit_answer))
        }
        if (state.guided) TextButton(onClick = { vm.miniHint() }, enabled = enabled) {
            Text(stringResource(R.string.hint))
        }
    }
}
