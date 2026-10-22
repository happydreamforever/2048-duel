package com.duel2048.app.game

import com.duel2048.app.cube.LocalCubeSession
import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeScramble
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.protocol.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SavedSessionTest {
    @Test
    fun `solo restored board uses original RNG for the next move`() {
        var board = GameEngine.newGame(819L)
        for (direction in Direction.entries) board = GameEngine.move(board, direction).state
        val saved = SavedGame("solo-roundtrip", SavedGameType.TILES, SavedGameMode.SOLO,
            board = board, durationMs = 0, remainingMs = 0)
        val controller = SoloController(0) { }
        controller.restore(saved)
        val direction = Direction.entries.first { GameEngine.move(board, it).events.moved }
        controller.swipe(direction)
        assertEquals(saved.id, controller.snapshot()!!.id)
        assertEquals(GameEngine.move(board, direction).state.syncKey(), controller.snapshot()!!.board!!.syncKey())
    }

    @Test
    fun `training restores both boards and a paused timer`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val saved = SavedGame("training-roundtrip", SavedGameType.TILES, SavedGameMode.TRAINING,
                board = GameEngine.move(GameEngine.newGame(9), Direction.LEFT).state,
                opponentBoard = GameEngine.move(GameEngine.newGame(9), Direction.UP).state,
                durationMs = 120_000, remainingMs = 39_000, difficulty = "hard")
            val session = LocalDuelSession(scope, TrainingProfile.HARD, "Alice", "Bot", saved.durationMs)
            session.start(saved)
            val restored = session.snapshot()
            assertEquals(saved.id, restored.id)
            assertEquals(saved.board!!.syncKey(), restored.board!!.syncKey())
            assertEquals(saved.opponentBoard!!.syncKey(), restored.opponentBoard!!.syncKey())
            assertTrue(restored.remainingMs in 38_000..39_000)
            assertEquals("hard", restored.difficulty)
            assertEquals(DuelPhase.PLAYING, session.state.value.phase)
        } finally { scope.cancel() }
    }

    @Test
    fun `cube restores middle slices and its solver starts from the saved position`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val saved = SavedGame("cube-roundtrip", SavedGameType.CUBE, SavedGameMode.TRAINING,
                seed = 888, played = listOf("M", "E'", "S2", "R", "U'"), durationMs = 120_000,
                remainingMs = 39_000, guided = true, difficulty = "easy")
            val session = LocalCubeSession(scope, TrainingProfile.EASY, "Alice", "Trainer", true, saved.durationMs) { 10_000 }
            session.start(saved)
            assertEquals(saved.played, session.snapshot()!!.played)
            assertEquals(saved.remainingMs, session.snapshot()!!.remainingMs)
            val position = CubeScramble.apply(saved.seed).applyAll(saved.played.map { CubeMove.parse(it)!! })
            val solution = session.revealSolver().split(" ").map { CubeMove.parse(it)!! }
            assertTrue(position.applyAll(solution).isSolved())
            session.applyMove(CubeMove.Mi)
            assertEquals(saved.played + "M'", session.snapshot()!!.played)
            assertEquals(saved.id, session.snapshot()!!.id)
        } finally { scope.cancel() }
    }
}
