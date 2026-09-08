package com.duel2048.app.game

import com.duel2048.shared.engine.Board
import com.duel2048.shared.engine.Cell
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.engine.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardUiBuilderTest {

    private fun state(): GameState {
        // row 0: 2 2 . 4  -> LEFT gives [4, 4, ., .] with one merge and one slide
        val cells = MutableList<Tile?>(16) { null }
        cells[0] = Tile(1, 2)
        cells[1] = Tile(2, 2)
        cells[3] = Tile(3, 4)
        return GameState(board = Board(4, cells), rngState = 9L, nextTileId = 10)
    }

    @Test
    fun `merge produces merged tile on top of two consumed sliding tiles`() {
        val r = GameEngine.move(state(), Direction.LEFT)
        val ui = BoardUiBuilder.fromMove(r.state, r.events, version = 1)

        val merged = ui.tiles.filter { it.kind == TileKind.MERGED }
        assertEquals(1, merged.size)
        assertEquals(4, merged[0].value)
        assertEquals(Cell(0, 0), merged[0].to)
        assertTrue(merged[0].z > 1f)

        val consumed = ui.tiles.filter { it.id == 1 || it.id == 2 }
        assertEquals(2, consumed.size)
        assertTrue(consumed.all { it.kind == TileKind.SLIDE && it.to == Cell(0, 0) && it.z == 0f })

        val slid = ui.tiles.first { it.id == 3 }
        assertEquals(TileKind.SLIDE, slid.kind)
        assertEquals(Cell(0, 3), slid.from)
        assertEquals(Cell(0, 1), slid.to)

        val spawned = ui.tiles.filter { it.kind == TileKind.SPAWN }
        assertEquals(1, spawned.size)
    }

    @Test
    fun `garbage placements are marked LANDED and everything else STATIC`() {
        val s = state()
        val g = GameEngine.addGarbage(s, 2)
        val ui = BoardUiBuilder.fromGarbage(g.state, g.placements, version = 2)
        assertEquals(2, ui.tiles.count { it.kind == TileKind.LANDED })
        assertEquals(3, ui.tiles.count { it.kind == TileKind.STATIC })
        assertTrue(ui.tiles.filter { it.kind == TileKind.LANDED }.all { it.isGarbage })
    }

    @Test
    fun `static snapshot keeps ids and positions`() {
        val s = state()
        val ui = BoardUiBuilder.fromState(s, version = 3)
        assertEquals(3, ui.tiles.size)
        assertTrue(ui.tiles.all { it.kind == TileKind.STATIC && it.from == it.to })
        assertEquals(setOf(1, 2, 3), ui.tiles.map { it.id }.toSet())
    }
}
