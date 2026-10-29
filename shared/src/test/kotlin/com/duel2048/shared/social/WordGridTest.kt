package com.duel2048.shared.social

import com.duel2048.shared.protocol.*
import kotlin.random.Random
import kotlin.test.*

class WordGridTest {
    private val grid = listOf("CATS", "XXXX", "BIRD", "FISH")

    @Test fun `paths allow horizontal vertical turns and reverse but no diagonals wrapping or reuse`() {
        assertEquals("CAT", WordGrid.selectedWord(grid, listOf(0, 1, 2)))
        assertEquals("CATS", WordGrid.selectedWord(grid, listOf(0, 1, 2, 3)))
        assertEquals("STAC", WordGrid.selectedWord(grid, listOf(3, 2, 1, 0)))
        assertEquals("CXBI", WordGrid.selectedWord(grid, listOf(0, 4, 8, 9)))
        assertNull(WordGrid.selectedWord(grid, listOf(0, 5)))
        assertNull(WordGrid.selectedWord(grid, listOf(3, 4)))
        assertNull(WordGrid.selectedWord(grid, listOf(0, 1, 0)))
        assertNull(WordGrid.selectedWord(grid, listOf(-1)))
        assertNull(WordGrid.selectedWord(grid, listOf(16)))
        assertNull(WordGrid.selectedWord(listOf("CATS"), listOf(0)))
        assertNull(WordGrid.selectedWord(grid, emptyList()))
        assertNull(WordGrid.findPath(grid, "CAXS"))
        assertEquals(listOf(0, 1, 2, 3), WordGrid.findPath(grid, "CATS"))
    }

    @Test fun `all sizes generate several playable dictionary words with deterministic seeds`() {
        val words = WordGrid.dictionary(PuzzleCatalog("/datasets/training"))
        for (size in WordGrid.sizes) for (seed in 0..30) {
            val generated = WordGrid.generate(size, words, Random(seed))
            assertEquals(generated, WordGrid.generate(size, words, Random(seed)))
            assertEquals(size, generated.size)
            assertTrue(generated.all { it.matches(Regex("[A-Z]{$size}")) })
            val paths = words.mapNotNull { word -> WordGrid.findPath(generated, word)?.let { word to it } }
            assertTrue(paths.size >= 2, "$size: $seed")
            paths.forEach { (word, path) -> assertEquals(word, WordGrid.selectedWord(generated, path)) }
        }
        assertEquals(8, WordGrid.score(listOf("CATS", "BIRD", "CATS")))
        assertFailsWith<IllegalArgumentException> { WordGrid.generate(5, words) }
    }

    @Test fun `puzzle saves preserve words and selected path while rejecting diagonal saves`() {
        val save = SavedGame("grid-save", SavedGameType.WORD_PUZZLE, SavedGameMode.TRAINING,
            grid = grid, difficulty = "4", foundWords = listOf("CATS"), selectedLetters = listOf(0, 1))
        save.validate()
        val restored = Protocol.json.decodeFromString(SavedGame.serializer(), Protocol.json.encodeToString(SavedGame.serializer(), save))
        assertEquals(save, restored)
        assertFailsWith<IllegalArgumentException> { save.copy(selectedLetters = listOf(0, 5)).validate() }
    }
}
