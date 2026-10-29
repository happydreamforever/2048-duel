package com.duel2048.shared.social

import com.duel2048.shared.protocol.*
import kotlin.random.Random
import kotlin.test.*

class PuzzlesTest {
    @Test
    fun `every math dataset has valid arithmetic and four distinct choices`() {
        for (level in Puzzles.mathLevels) {
            val entries = Puzzles.mathEntries(level)
            assertEquals(12, entries.size)
            for (problem in entries) {
                val (left, op, right) = problem.prompt.split(" ")
                val a = left.toBigInteger()
                val b = right.toBigInteger()
                val result = when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> {
                    assertEquals(0.toBigInteger(), a.remainder(b)); a / b
                }; else -> error("Unknown operator") }
                assertEquals(result.toString(), problem.answer, problem.id)
                assertEquals(4, problem.choices.distinct().size)
                assertEquals(1, problem.choices.count { it == problem.answer })
            }
            val drawn = (0..40).map { Puzzles.math(level, Random(it)) }
            assertTrue(drawn.map { it.id }.distinct().size > 4)
            assertTrue(drawn.all { picked -> entries.any { it.id == picked.id && it.answer == picked.answer } })
            assertTrue(drawn.map { it.choices.indexOf(it.answer) }.distinct().size > 1)
        }
    }

    @Test
    fun `dictionary levels contain matching word lengths and shuffled playable letter banks`() {
        for (length in Puzzles.wordLengths) {
            val entries = Puzzles.wordEntries(length)
            assertEquals(8, entries.size)
            assertTrue(entries.all { it.word.length == length && it.clue.isNotBlank() })
            for (seed in 0..40) {
                val puzzle = Puzzles.word(length, Random(seed))
                assertTrue(entries.any { it.word == puzzle.answer && it.clue == puzzle.prompt })
                assertEquals(puzzle.answer.toList().sorted(), puzzle.grid.single().toList().sorted())
                assertNotEquals(puzzle.answer, puzzle.grid.single())
            }
        }
    }

    @Test
    fun `letter selection allows repeated characters only from distinct tiles`() {
        val grid = listOf("TETREL")
        assertEquals("LETTER", Puzzles.selectedWord(grid, listOf(5, 1, 0, 2, 4, 3)))
        assertNull(Puzzles.selectedWord(grid, listOf(0, 0)))
        assertNull(Puzzles.selectedWord(grid, listOf(-1)))
        assertNull(Puzzles.selectedWord(grid, listOf(6)))
        assertNull(Puzzles.selectedWord(grid, emptyList()))
    }

    @Test
    fun `save roundtrip preserves choices puzzle identity and partly assembled word`() {
        val saved = SavedGame("word-progress", SavedGameType.WORD, SavedGameMode.TRAINING,
            grid = listOf("TETREL"), puzzleId = "word-6-letter", selectedLetters = listOf(5, 1, 0))
        val restored = Protocol.json.decodeFromString(SavedGame.serializer(), Protocol.json.encodeToString(SavedGame.serializer(), saved))
        restored.validate()
        assertEquals("LET", Puzzles.selectedWord(restored.grid, restored.selectedLetters))
        assertEquals(saved, restored)
        val math = saved.copy(game = SavedGameType.MATH, choices = listOf("0", "1", "2", "3"))
        assertEquals(math, Protocol.json.decodeFromString(SavedGame.serializer(), Protocol.json.encodeToString(SavedGame.serializer(), math)))
        assertFailsWith<IllegalArgumentException> { saved.copy(selectedLetters = listOf(0, 0)).validate() }
    }

    @Test
    fun `CSV supports commas and escaped quotes in clues`() {
        assertEquals(listOf("WORD", "A clue, with a \"quote\""), Puzzles.csv("WORD,\"A clue, with a \"\"quote\"\"\""))
        assertFailsWith<IllegalArgumentException> { Puzzles.csv("WORD,\"unfinished") }
    }

    @Test
    fun `shared library includes only the limited training resources`() {
        assertNull(Puzzles::class.java.getResourceAsStream("/datasets/matches/math/easy.csv"))
        assertNull(Puzzles::class.java.getResourceAsStream("/datasets/matches/words/4.csv"))
        assertEquals(48, Puzzles.mathLevels.sumOf { Puzzles.mathEntries(it).size })
        assertEquals(40, Puzzles.wordLengths.sumOf { Puzzles.wordEntries(it).size })
    }
}
