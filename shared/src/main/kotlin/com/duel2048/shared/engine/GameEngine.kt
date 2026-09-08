package com.duel2048.shared.engine

/**
 * Deterministic 2048 engine with duel extensions (attack energy, garbage tiles).
 *
 * All functions are pure: given the same [GameState] and input they produce the same
 * result on every platform, which lets the Android client predict its own moves
 * instantly while the server stays authoritative.
 */
object GameEngine {

    fun newGame(seed: Long, size: Int = Rules.BOARD_SIZE, initialTiles: Int = Rules.INITIAL_TILES): GameState {
        var state = GameState(board = Board.empty(size), rngState = seed)
        repeat(initialTiles) { state = spawn(state).first }
        return state.copy(gameOver = !state.board.hasMoves())
    }

    fun move(state: GameState, direction: Direction): MoveResult {
        if (state.gameOver) return MoveResult(state, MoveEvents.NONE)

        val size = state.board.size
        val cells = MutableList<Tile?>(size * size) { null }
        val movements = ArrayList<TileMove>()
        val consumed = ArrayList<TileMove>()
        val merges = ArrayList<MergeEvent>()
        var nextId = state.nextTileId
        var scoreGained = 0

        for (line in lines(size, direction)) {
            var write = 0
            var lastTile: Tile? = null
            var lastFrom: Cell? = null
            for (from in line) {
                val tile = state.board[from] ?: continue
                val prev = lastTile
                if (prev != null && lastFrom != null && !prev.isGarbage && !tile.isGarbage && prev.value == tile.value) {
                    val target = line[write - 1]
                    val merged = Tile(nextId++, tile.value * 2)
                    cells[target.row * size + target.col] = merged
                    consumed += TileMove(prev.id, prev.value, prev.garbageHp, lastFrom, target)
                    consumed += TileMove(tile.id, tile.value, tile.garbageHp, from, target)
                    merges += MergeEvent(target, merged.value, merged.id, listOf(prev.id, tile.id))
                    scoreGained += merged.value
                    lastTile = null
                    lastFrom = null
                } else {
                    val target = line[write]
                    cells[target.row * size + target.col] = tile
                    if (target != from) movements += TileMove(tile.id, tile.value, tile.garbageHp, from, target)
                    lastTile = tile
                    lastFrom = from
                    write++
                }
            }
        }

        val moved = movements.isNotEmpty() || merges.isNotEmpty()
        if (!moved) return MoveResult(state, MoveEvents.NONE)

        var board = Board(size, cells)

        // Merges crack neighbouring garbage tiles.
        val damage = LinkedHashMap<Cell, Int>()
        for (m in merges) {
            for (d in Direction.entries) {
                val n = Cell(m.at.row + d.dRow, m.at.col + d.dCol)
                if (!board.contains(n)) continue
                val g = board[n] ?: continue
                if (g.isGarbage) damage[n] = (damage[n] ?: 0) + 1
            }
        }
        val damaged = ArrayList<Cell>()
        val shattered = ArrayList<Cell>()
        for ((cell, dmg) in damage) {
            val g = board[cell] ?: continue
            val hp = g.garbageHp - dmg
            if (hp <= 0) {
                board = board.with(cell, null)
                shattered += cell
            } else {
                board = board.with(cell, g.copy(garbageHp = hp))
                damaged += cell
            }
        }

        // Attack energy.
        val energyGained = merges.sumOf { Rules.energyForMerge(it.value) } + Rules.comboBonus(merges.size)
        var energy = state.energy + energyGained
        val garbageSent = minOf(Rules.MAX_GARBAGE_PER_MOVE, energy / Rules.ATTACK_COST)
        energy -= garbageSent * Rules.ATTACK_COST

        var next = state.copy(
            board = board,
            score = state.score + scoreGained,
            moves = state.moves + 1,
            nextTileId = nextId,
            energy = energy,
            garbageSentTotal = state.garbageSentTotal + garbageSent,
            mergesTotal = state.mergesTotal + merges.size,
            bestCombo = maxOf(state.bestCombo, merges.size),
        )
        val (spawnedState, spawnEvent) = spawn(next)
        next = spawnedState.copy(gameOver = !spawnedState.board.hasMoves())

        return MoveResult(
            next,
            MoveEvents(
                moved = true,
                movements = movements,
                consumed = consumed,
                merges = merges,
                spawned = spawnEvent,
                garbageDamaged = damaged,
                garbageShattered = shattered,
                scoreGained = scoreGained,
                energyGained = energyGained,
                garbageSent = garbageSent,
            ),
        )
    }

    /** Drops [count] garbage tiles onto random empty cells (fewer if the board is nearly full). */
    fun addGarbage(state: GameState, count: Int): GarbageResult {
        var board = state.board
        val rng = SplitMix64(state.rngState)
        var nextId = state.nextTileId
        val placed = ArrayList<GarbagePlacement>()
        for (i in 0 until count) {
            val empty = board.emptyCells()
            if (empty.isEmpty()) break
            val cell = empty[rng.nextInt(empty.size)]
            val tile = Tile(nextId++, 0, Rules.GARBAGE_HP)
            board = board.with(cell, tile)
            placed += GarbagePlacement(cell, tile)
        }
        val next = state.copy(
            board = board,
            rngState = rng.state,
            nextTileId = nextId,
            garbageReceivedTotal = state.garbageReceivedTotal + placed.size,
        )
        return GarbageResult(next.copy(gameOver = !next.board.hasMoves()), placed)
    }

    fun canMove(state: GameState, direction: Direction): Boolean = move(state, direction).events.moved

    private fun spawn(state: GameState): Pair<GameState, SpawnEvent?> {
        val empty = state.board.emptyCells()
        if (empty.isEmpty()) return state to null
        val rng = SplitMix64(state.rngState)
        val cell = empty[rng.nextInt(empty.size)]
        val value = if (rng.nextInt(10) < Rules.FOUR_CHANCE_IN_TEN) 4 else 2
        val tile = Tile(state.nextTileId, value)
        val next = state.copy(
            board = state.board.with(cell, tile),
            rngState = rng.state,
            nextTileId = state.nextTileId + 1,
        )
        return next to SpawnEvent(cell, tile)
    }

    /** Cells of every line, ordered from the edge tiles slide towards. */
    private fun lines(size: Int, direction: Direction): List<List<Cell>> = when (direction) {
        Direction.LEFT -> (0 until size).map { r -> (0 until size).map { c -> Cell(r, c) } }
        Direction.RIGHT -> (0 until size).map { r -> (size - 1 downTo 0).map { c -> Cell(r, c) } }
        Direction.UP -> (0 until size).map { c -> (0 until size).map { r -> Cell(r, c) } }
        Direction.DOWN -> (0 until size).map { c -> (size - 1 downTo 0).map { r -> Cell(r, c) } }
    }
}
