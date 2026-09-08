package com.duel2048.app.game

import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.engine.MoveEvents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class SoloUiState(
    val board: BoardUi = BoardUi.empty(4),
    val score: Int = 0,
    val best: Int = 0,
    val maxTile: Int = 0,
    val moves: Int = 0,
    val gameOver: Boolean = false,
    val energy: Int = 0,
)

/** Offline single-player mode using the very same engine. */
class SoloController(initialBest: Int, private val onNewBest: (Int) -> Unit) {

    private val _state = MutableStateFlow(SoloUiState(best = initialBest))
    val state: StateFlow<SoloUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<GameEffect>(extraBufferCapacity = 64)
    val effects: SharedFlow<GameEffect> = _effects.asSharedFlow()

    private var game: GameState? = null
    private var version = 0L

    fun ensureGame() {
        if (game == null) newGame()
    }

    fun newGame() {
        val g = GameEngine.newGame(Random.nextLong())
        game = g
        version++
        _state.update { it.copy(board = BoardUiBuilder.fromState(g, version, TileKind.SPAWN), score = 0, maxTile = g.board.maxValue(), moves = 0, gameOver = false, energy = 0) }
    }

    fun swipe(direction: Direction) {
        val g = game ?: return
        if (g.gameOver) return
        val r = GameEngine.move(g, direction)
        if (!r.events.moved) {
            _effects.tryEmit(GameEffect.NoMove(direction))
            return
        }
        game = r.state
        version++
        val best = maxOf(_state.value.best, r.state.score)
        if (best > _state.value.best) onNewBest(best)
        _state.update {
            it.copy(
                board = BoardUiBuilder.fromMove(r.state, r.events, version),
                score = r.state.score,
                best = best,
                maxTile = r.state.board.maxValue(),
                moves = r.state.moves,
                gameOver = r.state.gameOver,
                energy = r.state.energy,
            )
        }
        emit(r.events)
    }

    private fun emit(ev: MoveEvents) {
        if (ev.merges.isNotEmpty()) {
            _effects.tryEmit(GameEffect.Merges(ev.merges.map { it.at to it.value }, ev.combo, ev.scoreGained, mine = true))
            val best = ev.merges.maxOf { it.value }
            if (best >= 256) _effects.tryEmit(GameEffect.Milestone(best))
        }
        if (game?.gameOver == true) _effects.tryEmit(GameEffect.MatchEnd(won = false, draw = false))
    }
}
