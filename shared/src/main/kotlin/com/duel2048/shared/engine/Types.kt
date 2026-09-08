package com.duel2048.shared.engine

import kotlinx.serialization.Serializable

/** Swipe direction. dRow/dCol describe the unit step in that direction. */
@Serializable
enum class Direction(val dRow: Int, val dCol: Int) {
    UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1)
}

@Serializable
data class Cell(val row: Int, val col: Int)

/**
 * A tile on the board. Every tile carries a unique id so the UI can animate it
 * across moves (a pattern borrowed from alexjlockwood/compose-multiplatform-2048).
 *
 * Garbage tiles (sent by the opponent) have [garbageHp] > 0 and value 0. They slide
 * like normal tiles but never merge; each merge that happens next to them removes 1 hp.
 */
@Serializable
data class Tile(val id: Int, val value: Int, val garbageHp: Int = 0) {
    val isGarbage: Boolean get() = garbageHp > 0
}

@Serializable
data class Board(val size: Int, val cells: List<Tile?>) {

    init {
        require(cells.size == size * size) { "board must have size*size cells" }
    }

    operator fun get(row: Int, col: Int): Tile? = cells[row * size + col]
    operator fun get(cell: Cell): Tile? = get(cell.row, cell.col)

    fun contains(cell: Cell): Boolean = cell.row in 0 until size && cell.col in 0 until size

    fun tiles(): List<Pair<Cell, Tile>> =
        cells.mapIndexedNotNull { i, t -> t?.let { Cell(i / size, i % size) to it } }

    fun emptyCells(): List<Cell> =
        cells.mapIndexedNotNull { i, t -> if (t == null) Cell(i / size, i % size) else null }

    fun maxValue(): Int = cells.maxOfOrNull { it?.value ?: 0 } ?: 0

    fun garbageCount(): Int = cells.count { it?.isGarbage == true }

    fun with(cell: Cell, tile: Tile?): Board =
        Board(size, cells.toMutableList().also { it[cell.row * size + cell.col] = tile })

    /** True while at least one move would change the board. */
    fun hasMoves(): Boolean {
        if (cells.any { it == null }) return true
        for (r in 0 until size) {
            for (c in 0 until size) {
                val t = get(r, c) ?: continue
                if (t.isGarbage) continue
                if (c + 1 < size) {
                    val n = get(r, c + 1)
                    if (n != null && !n.isGarbage && n.value == t.value) return true
                }
                if (r + 1 < size) {
                    val n = get(r + 1, c)
                    if (n != null && !n.isGarbage && n.value == t.value) return true
                }
            }
        }
        return false
    }

    /** Compact textual form used for sync checks and debugging. */
    fun signature(): String = cells.joinToString(",") { t ->
        when {
            t == null -> "."
            t.isGarbage -> "g${t.garbageHp}"
            else -> t.value.toString()
        }
    }

    companion object {
        fun empty(size: Int): Board = Board(size, List(size * size) { null })
    }
}

/** Full state of one player's game. Everything needed to replay deterministically is here. */
@Serializable
data class GameState(
    val board: Board,
    val score: Int = 0,
    val moves: Int = 0,
    val rngState: Long,
    val nextTileId: Int = 1,
    /** Attack meter. Every [Rules.ATTACK_COST] points send one garbage tile. */
    val energy: Int = 0,
    val garbageSentTotal: Int = 0,
    val garbageReceivedTotal: Int = 0,
    val mergesTotal: Int = 0,
    val bestCombo: Int = 0,
    val gameOver: Boolean = false,
) {
    /** Key used by the client to verify its predicted state matches the server's. */
    fun syncKey(): String = "${board.signature()}|$score|$energy|$rngState|$nextTileId"
}

/** A tile that survived a move and changed position. */
@Serializable
data class TileMove(val id: Int, val value: Int, val garbageHp: Int, val from: Cell, val to: Cell)

/** Two tiles merged into a new tile [newId] at [at]. */
@Serializable
data class MergeEvent(val at: Cell, val value: Int, val newId: Int, val sourceIds: List<Int>)

@Serializable
data class SpawnEvent(val at: Cell, val tile: Tile)

@Serializable
data class GarbagePlacement(val at: Cell, val tile: Tile)

/** Everything the UI needs to animate one move. */
@Serializable
data class MoveEvents(
    val moved: Boolean,
    val movements: List<TileMove> = emptyList(),
    /** Tiles that merged away: they slide to the merge cell and disappear underneath the new tile. */
    val consumed: List<TileMove> = emptyList(),
    val merges: List<MergeEvent> = emptyList(),
    val spawned: SpawnEvent? = null,
    val garbageDamaged: List<Cell> = emptyList(),
    val garbageShattered: List<Cell> = emptyList(),
    val scoreGained: Int = 0,
    val energyGained: Int = 0,
    val garbageSent: Int = 0,
) {
    val combo: Int get() = merges.size

    companion object {
        val NONE = MoveEvents(moved = false)
    }
}

data class MoveResult(val state: GameState, val events: MoveEvents)

data class GarbageResult(val state: GameState, val placements: List<GarbagePlacement>)
