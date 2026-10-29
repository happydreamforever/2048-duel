package com.duel2048.app.game

import com.duel2048.shared.social.Puzzles
import org.junit.Assert.*
import org.junit.Test

class TrainingCatalogTest {
    @Test
    fun `client classpath contains training examples but no full competition catalog`() {
        assertEquals(48, Puzzles.mathLevels.sumOf { Puzzles.mathEntries(it).size })
        assertEquals(48, Puzzles.wordLengths.sumOf { Puzzles.wordEntries(it).size })
        assertNull(javaClass.getResourceAsStream("/datasets/matches/math/easy.csv"))
        assertNull(javaClass.getResourceAsStream("/datasets/matches/words/8.csv"))
    }
}
