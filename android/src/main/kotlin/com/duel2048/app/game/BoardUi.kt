package com.duel2048.app.game

import com.duel2048.shared.engine.Cell
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.engine.GarbagePlacement
import com.duel2048.shared.engine.MoveEvents

/** How a tile should animate when a new [BoardUi] version is shown. */
enum class TileKind { STATIC, SLIDE, MERGED, SPAWN, LANDED }

data class TileUi(
    val id: Int,
    val value: Int,
    val garbageHp: Int,
    val from: Cell,
    val to: Cell,
    val kind: TileKind,
    val z: Float,
    val version: Long,
) {
    val isGarbage: Boolean get() = garbageHp > 0
}

data class BoardUi(val size: Int, val tiles: List<TileUi>, val version: Long) {
    companion object {
        fun empty(size: Int) = BoardUi(size, emptyList(), 0L)
    }
}

/**
 * Turns engine output into a render model. Tiles keep their engine ids so Compose can
 * animate each one across versions (see BoardView). Merged-away tiles are kept for one
 * version so they visibly slide under the new tile before disappearing.
 */
object BoardUiBuilder {

    fun fromState(state: GameState, version: Long, kind: TileKind = TileKind.STATIC): BoardUi {
        val tiles = state.board.tiles().map { (cell, t) ->
            TileUi(t.id, t.value, t.garbageHp, cell, cell, kind, 1f, version)
        }
        return BoardUi(state.board.size, tiles, version)
    }

    fun fromMove(state: GameState, ev: MoveEvents, version: Long): BoardUi {
        if (!ev.moved) return fromState(state, version)
        val moveById = ev.movements.associateBy { it.id }
        val mergedIds = ev.merges.map { it.newId }.toHashSet()
        val spawnId = ev.spawned?.tile?.id
        val tiles = ArrayList<TileUi>(state.board.size * state.board.size + ev.consumed.size)
        for ((cell, t) in state.board.tiles()) {
            val mv = moveById[t.id]
            val kind = when {
                t.id in mergedIds -> TileKind.MERGED
                t.id == spawnId -> TileKind.SPAWN
                mv != null -> TileKind.SLIDE
                else -> TileKind.STATIC
            }
            tiles += TileUi(t.id, t.value, t.garbageHp, mv?.from ?: cell, cell, kind, if (kind == TileKind.MERGED) 2f else 1f, version)
        }
        for (c in ev.consumed) {
            tiles += TileUi(c.id, c.value, c.garbageHp, c.from, c.to, TileKind.SLIDE, 0f, version)
        }
        return BoardUi(state.board.size, tiles, version)
    }

    fun fromGarbage(state: GameState, placements: List<GarbagePlacement>, version: Long): BoardUi {
        val landed = placements.map { it.tile.id }.toHashSet()
        val tiles = state.board.tiles().map { (cell, t) ->
            val kind = if (t.id in landed) TileKind.LANDED else TileKind.STATIC
            TileUi(t.id, t.value, t.garbageHp, cell, cell, kind, if (kind == TileKind.LANDED) 3f else 1f, version)
        }
        return BoardUi(state.board.size, tiles, version)
    }
}
