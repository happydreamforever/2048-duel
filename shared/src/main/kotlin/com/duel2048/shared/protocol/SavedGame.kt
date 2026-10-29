package com.duel2048.shared.protocol

import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.social.WordGrid
import kotlinx.serialization.Serializable

@Serializable
enum class SavedGameType { TILES, CUBE, MATH, WORD, WORD_PUZZLE }

@Serializable
enum class SavedGameMode { SOLO, TRAINING, BOT, ONLINE }

/** Versioned account-owned checkpoint. Only the server writes ONLINE checkpoints. */
@Serializable
data class SavedGame(
    val id: String,
    val game: SavedGameType,
    val mode: SavedGameMode,
    val revision: Long = 0,
    val updatedAt: Long = 0,
    val completed: Boolean = false,
    val deleted: Boolean = false,
    val remainingMs: Long = 300_000,
    val durationMs: Long = 300_000,
    val seed: Long = 0,
    val played: List<String> = emptyList(),
    val opponentPlayed: List<String> = emptyList(),
    val board: GameState? = null,
    val opponentBoard: GameState? = null,
    val difficulty: String = "normal",
    val guided: Boolean = false,
    val opponentName: String = "Bot",
    val prompt: String = "",
    val grid: List<String> = emptyList(),
    val solution: String = "",
    val roomId: String = "",
    val expectedSeq: Int = 0,
    val started: Boolean = true,
    val countdown: Int = 0,
    val you: PlayerInfo? = null,
    val opponent: PlayerInfo? = null,
    val duelResult: MatchOver? = null,
    val cubeResult: CubeMatchOver? = null,
    val resumable: Boolean = true,
    val choices: List<String> = emptyList(),
    val selectedLetters: List<Int> = emptyList(),
    val puzzleId: String = "",
    val puzzleMode: String = "",
    val foundWords: List<String> = emptyList(),
) {
    fun validate() {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid save id" }
        require(revision >= 0 && expectedSeq >= 0) { "Invalid revision" }
        require(durationMs in 0..86_400_000 && remainingMs in 0..durationMs) { "Invalid timer" }
        require(played.size <= 20_000 && opponentPlayed.size <= 20_000) { "History is too large" }
        require((played + opponentPlayed).all { it.length <= 8 }) { "Invalid move" }
        if (game == SavedGameType.CUBE) require((played + opponentPlayed).all { CubeMove.parse(it) != null }) { "Unknown cube move" }
        for (state in listOfNotNull(board, opponentBoard)) {
            require(state.board.size in 2..8) { "Invalid board size" }
            require(state.score >= 0 && state.moves >= 0) { "Invalid counters" }
        }
        if (game == SavedGameType.TILES) require(board != null && (mode == SavedGameMode.SOLO || opponentBoard != null)) { "Board state is missing" }
        require(prompt.length <= 1000 && solution.length <= 1000 && roomId.length <= 100) { "Puzzle is too large" }
        require(grid.size <= 8 && grid.all { it.length <= 16 }) { "Invalid word grid" }
        require(choices.size <= 4 && choices.all { it.length <= 100 } && choices.distinct().size == choices.size) { "Invalid answer choices" }
        require(foundWords.size <= 500 && foundWords.distinct().size == foundWords.size && foundWords.all { it.matches(Regex("[A-Z]{3,8}")) }) { "Invalid found words" }
        if (game == SavedGameType.WORD_PUZZLE) {
            require(grid.size in WordGrid.sizes && grid.all { it.length == grid.size && it.all { c -> c in 'A'..'Z' } }) { "Invalid puzzle grid" }
            require(selectedLetters.isEmpty() || WordGrid.selectedWord(grid, selectedLetters) != null) { "Invalid puzzle path" }
        }
        require(puzzleId.length <= 100) { "Invalid puzzle id" }
        require(puzzleMode in listOf("", "train", "bot", "pvp")) { "Invalid puzzle mode" }
        require(selectedLetters.size <= 16 && selectedLetters.distinct().size == selectedLetters.size && selectedLetters.all { it in 0 until grid.sumOf(String::length) }) { "Invalid selected letters" }
    }
}

@Serializable
data class SavedGameList(val games: List<SavedGame>)
