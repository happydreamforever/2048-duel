package com.duel2048.shared

import com.duel2048.shared.bot.Bot
import com.duel2048.shared.engine.Board
import com.duel2048.shared.engine.Cell
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.engine.Rules
import com.duel2048.shared.engine.SplitMix64
import com.duel2048.shared.engine.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameEngineTest {

    private fun board(vararg rows: String): Board {
        val size = rows.size
        var id = 1
        val cells = rows.flatMap { row ->
            row.split(" ").map { tok ->
                when {
                    tok == "." -> null
                    tok.startsWith("g") -> Tile(id++, 0, tok.drop(1).toInt())
                    else -> Tile(id++, tok.toInt())
                }
            }
        }
        return Board(size, cells)
    }

    private fun state(b: Board) = GameState(board = b, rngState = 42L, nextTileId = 100)

    @Test
    fun `left move slides and merges once per pair`() {
        val r = GameEngine.move(state(board("2 2 2 2", ". . . .", ". . . .", ". . . .")), Direction.LEFT)
        assertTrue(r.events.moved)
        assertEquals(2, r.events.merges.size)
        assertEquals(8, r.events.scoreGained)
        assertEquals(4, r.state.board[0, 0]!!.value)
        assertEquals(4, r.state.board[0, 1]!!.value)
        // A tile spawned somewhere in the empty area.
        assertNotNull(r.events.spawned)
        assertEquals(3, r.state.board.tiles().size)
    }

    @Test
    fun `no double merge in one move`() {
        val r = GameEngine.move(state(board("4 2 2 .", ". . . .", ". . . .", ". . . .")), Direction.LEFT)
        assertEquals(1, r.events.merges.size)
        assertEquals(4, r.state.board[0, 0]!!.value)
        assertEquals(4, r.state.board[0, 1]!!.value)
    }

    @Test
    fun `unchanged board is not a move`() {
        val r = GameEngine.move(state(board("2 4 8 16", ". . . .", ". . . .", ". . . .")), Direction.LEFT)
        assertFalse(r.events.moved)
        assertEquals(0, r.events.merges.size)
    }

    @Test
    fun `all four directions work`() {
        val b = board("2 . . 2", ". . . .", ". . . .", "2 . . 2")
        assertEquals(2, GameEngine.move(state(b), Direction.UP).events.merges.size)
        assertEquals(2, GameEngine.move(state(b), Direction.DOWN).events.merges.size)
        assertEquals(2, GameEngine.move(state(b), Direction.LEFT).events.merges.size)
        assertEquals(2, GameEngine.move(state(b), Direction.RIGHT).events.merges.size)
    }

    @Test
    fun `garbage slides but never merges and shatters after two adjacent merges`() {
        val r = GameEngine.move(state(board("2 2 g2 .", ". . . .", ". . . .", ". . . .")), Direction.LEFT)
        assertEquals(1, r.events.merges.size)
        val g = r.state.board[0, 1]
        assertNotNull(g)
        assertTrue(g.isGarbage)
        assertEquals(1, g.garbageHp, "merge next to garbage removes 1 hp")
        assertEquals(listOf(Cell(0, 1)), r.events.garbageDamaged)

        val r2 = GameEngine.move(state(board("2 2 g1 .", ". . . .", ". . . .", ". . . .")), Direction.LEFT)
        assertEquals(listOf(Cell(0, 1)), r2.events.garbageShattered)
        assertTrue(r2.state.board[0, 1] == null || !r2.state.board[0, 1]!!.isGarbage)
    }

    @Test
    fun `same seed gives identical games`() {
        var a = GameEngine.newGame(1234L)
        var b = GameEngine.newGame(1234L)
        val dirs = listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)
        repeat(60) { i ->
            a = GameEngine.move(a, dirs[i % 4]).state
            b = GameEngine.move(b, dirs[i % 4]).state
            assertEquals(a.syncKey(), b.syncKey())
        }
    }

    @Test
    fun `energy converts to garbage`() {
        val s = state(board("64 64 32 32", ". . . .", ". . . .", ". . . .")).copy(energy = Rules.ATTACK_COST - 1)
        val r = GameEngine.move(s, Direction.LEFT)
        val gained = Rules.energyForMerge(128) + Rules.energyForMerge(64) + Rules.comboBonus(2)
        assertEquals(gained, r.events.energyGained)
        val total = Rules.ATTACK_COST - 1 + gained
        assertEquals(minOf(Rules.MAX_GARBAGE_PER_MOVE, total / Rules.ATTACK_COST), r.events.garbageSent)
        assertEquals(total - r.events.garbageSent * Rules.ATTACK_COST, r.state.energy)
    }

    @Test
    fun `garbage placement fills empties and can end the game`() {
        val s = state(board("2 4 8 16", "32 64 128 256", "512 1024 2 4", "8 16 32 ."))
        val g = GameEngine.addGarbage(s, 2)
        assertEquals(1, g.placements.size)
        assertTrue(g.state.gameOver)
    }

    @Test
    fun `rng is stable`() {
        val r = SplitMix64(7L)
        val first = List(5) { r.nextInt(1000) }
        val r2 = SplitMix64(7L)
        assertEquals(first, List(5) { r2.nextInt(1000) })
    }

    @Test
    fun `bot always finds a legal move`() {
        var s = GameEngine.newGame(99L)
        var moves = 0
        while (!s.gameOver && moves < 400) {
            val d = Bot.chooseMove(s) ?: break
            val r = GameEngine.move(s, d)
            assertTrue(r.events.moved)
            s = r.state
            moves++
        }
        assertTrue(moves > 50, "bot should survive a while, made $moves moves")
    }
}
