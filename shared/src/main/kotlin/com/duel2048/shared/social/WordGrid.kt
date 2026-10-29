package com.duel2048.shared.social

import kotlin.math.abs
import kotlin.random.Random

/** Word Puzzle rules, separate from the clue-based Word Quiz letter bank. */
object WordGrid {
    val sizes = listOf(4, 6, 8)
    const val seconds = 120

    fun dictionary(catalog: PuzzleCatalog): Set<String> =
        Puzzles.wordLengths.flatMap { catalog.wordEntries(it) }.map { it.word }.toSet()

    fun generate(size: Int, words: Set<String>, random: Random = Random.Default): List<String> {
        require(size in sizes && words.isNotEmpty())
        val letters = "EEEEEEEEAAAARRRIIIOOOTTNNSSLLCCDDUUMPGHB"
        val cells = CharArray(size * size) { letters.random(random) }
        // A serpentine route guarantees several playable words, including turns between rows.
        var route = (0 until size).flatMap { row ->
            (if (row % 2 == 0) 0 until size else (size - 1 downTo 0)).map { row * size + it }
        }
        if (random.nextBoolean()) route = route.reversed()
        if (random.nextBoolean()) route = route.map { (it % size) * size + it / size }
        var offset = 0
        for (word in words.shuffled(random)) {
            if (word.length > cells.size - offset) continue
            word.forEachIndexed { i, letter -> cells[route[offset + i]] = letter }
            offset += word.length
        }
        return cells.toList().chunked(size).map { it.joinToString("") }
    }

    fun selectedWord(grid: List<String>, indices: List<Int>): String? {
        val size = grid.size
        if (size !in sizes || grid.any { it.length != size || it.any { c -> c !in 'A'..'Z' } }) return null
        if (indices.isEmpty() || indices.size > 8 || indices.distinct().size != indices.size || indices.any { it !in 0 until size * size }) return null
        if (indices.zipWithNext().any { (a, b) -> abs(a / size - b / size) + abs(a % size - b % size) != 1 }) return null
        return indices.map { grid[it / size][it % size] }.joinToString("")
    }

    fun score(words: Collection<String>): Int = words.distinct().sumOf { it.length }

    /** Used for the bot and end-of-round review; dictionaries and solutions stay on the server. */
    fun findPath(grid: List<String>, word: String): List<Int>? {
        if (grid.size !in sizes || grid.any { it.length != grid.size } || word.length !in 4..8) return null
        val size = grid.size
        val path = mutableListOf<Int>()
        fun visit(cell: Int, offset: Int): Boolean {
            if (cell in path || grid[cell / size][cell % size] != word[offset]) return false
            path.add(cell)
            if (offset == word.lastIndex) return true
            val row = cell / size; val col = cell % size
            val neighbors = listOfNotNull(
                (cell - size).takeIf { row > 0 }, (cell + size).takeIf { row < size - 1 },
                (cell - 1).takeIf { col > 0 }, (cell + 1).takeIf { col < size - 1 },
            )
            if (neighbors.any { visit(it, offset + 1) }) return true
            path.removeAt(path.lastIndex)
            return false
        }
        return (0 until size * size).firstOrNull { visit(it, 0) }?.let { path.toList() }
    }
}
