package com.duel2048.server

import com.duel2048.server.db.JsonDb
import com.duel2048.shared.social.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlin.random.Random

class PuzzleMatchTest {
    private val catalog = PuzzleCatalog("/datasets/matches")

    @Test
    fun `full server catalog contains valid problems beyond the training subset`() {
        for (level in Puzzles.mathLevels) {
            val entries = catalog.mathEntries(level)
            assertEquals(80, entries.size)
            assertTrue(entries.size > Puzzles.mathEntries(level).size)
            for (problem in entries) {
                val (a, op, b) = problem.prompt.split(" ")
                val left = a.toBigInteger(); val right = b.toBigInteger()
                val expected = when (op) { "+" -> left + right; "-" -> left - right; "*" -> left * right; else -> {
                    assertEquals(0.toBigInteger(), left.remainder(right)); left / right
                } }
                assertEquals(expected.toString(), problem.answer)
                assertEquals(4, problem.choices.distinct().size)
                assertEquals(1, problem.choices.count { it == problem.answer })
            }
        }
        for (length in Puzzles.wordLengths) {
            assertEquals(24, catalog.wordEntries(length).size)
            for (seed in 0..30) {
                val word = catalog.word(length, Random(seed))
                assertFalse(word.id.contains(word.answer, ignoreCase = true))
                assertEquals(word.answer.toList().sorted(), word.grid.single().toList().sorted())
            }
        }
    }

    @Test
    fun `math competitors get identical persisted choices and cannot retry a wrong answer`() = fixture { service ->
        val first = service().joinMini("Alice", "math", "pvp", "hard")
        val second = service().joinMini("Bobby", "math", "pvp", "hard")
        assertEquals(first.puzzleId, second.puzzleId)
        assertEquals(first.choices, second.choices)
        assertTrue(first.solution.isEmpty())
        val answer = catalog.mathEntries("hard").single { it.id == first.puzzleId }.answer
        val wrong = first.choices.first { it != answer }
        val pending = service().answer(first.roomId, "Alice", wrong)!!
        assertTrue(pending.answered)
        assertEquals("playing", pending.status)
        assertTrue(pending.solution.isEmpty())
        assertNull(service().answer(first.roomId, "Alice", answer)!!.winner)
        assertEquals("Bobby", service().answer(first.roomId, "Bobby", answer)!!.winner)
        assertEquals(first.choices, service().mini(first.roomId, "Alice")!!.choices)
    }

    @Test
    fun `word match derives answer from distinct tile indices rather than trusting typed text`() = fixture { service ->
        val room = service().joinMini("Alice", "word", "bot", "8")
        val entry = catalog.wordEntries(8).single { it.clue == room.prompt }
        assertTrue(room.solution.isEmpty())
        assertFalse(room.puzzleId.contains(entry.word, ignoreCase = true))
        assertEquals("playing", service().answer(room.roomId, "Alice", entry.word)!!.status)
        assertFalse(service().answer(room.roomId, "Alice", "", listOf(0, 0))!!.answered)
        val remaining = room.grid.single().mapIndexed { i, c -> i to c }.toMutableList()
        val selection = entry.word.map { letter ->
            val tile = remaining.first { it.second == letter }
            remaining.remove(tile)
            tile.first
        }
        val over = service().answer(room.roomId, "Alice", "ignored text", selection)!!
        assertEquals("Alice", over.winner)
        assertEquals(entry.word, over.solution)
        assertEquals("over", service().mini(room.roomId, "Alice")!!.status)
    }

    @Test
    fun `bot matches use server datasets and finish on a wrong selected choice`() = fixture { service ->
        val room = service().joinMini("Alice", "math", "bot", "medium")
        assertTrue(catalog.mathEntries("medium").any { it.id == room.puzzleId && it.prompt == room.prompt })
        val solution = catalog.mathEntries("medium").single { it.id == room.puzzleId }.answer
        assertFalse(service().answer(room.roomId, "Alice", "not a choice")!!.answered)
        val over = service().answer(room.roomId, "Alice", room.choices.first { it != solution })!!
        assertEquals("over", over.status)
        assertEquals("Bot", over.winner)
        assertFailsWith<IllegalArgumentException> { service().joinMini("Alice", "math", "train", "easy") }
        assertFailsWith<IllegalArgumentException> { service().joinMini("Alice", "word", "bot", "99") }
    }

    private fun fixture(test: (() -> SocialService) -> Unit) {
        val dir = Files.createTempDirectory("puzzle-matches").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val db = JsonDb(File(dir, "users.json")).load()
            val lobby = Lobby(ServerConfig.fromEnv(mapOf("DB" to "json", "DATA_DIR" to dir.path)), scope, db)
            test { SocialService(dir.path, db, lobby) }
        } finally { scope.cancel(); dir.deleteRecursively() }
    }
}
