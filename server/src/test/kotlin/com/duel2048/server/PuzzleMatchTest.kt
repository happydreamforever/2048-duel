package com.duel2048.server

import com.duel2048.server.db.JsonDb
import com.duel2048.shared.social.*
import kotlinx.coroutines.*
import com.duel2048.shared.protocol.Protocol
import kotlinx.serialization.json.*
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

    @Test
    fun `grid competitors share boards and earn persisted independent scores without duplicate rewards`() = fixture { service ->
        for (size in WordGrid.sizes) {
            val first = service().joinMini("Alice", "word_puzzle", "pvp", size.toString())
            val second = service().joinMini("Bobby", "word_puzzle", "pvp", size.toString())
            assertEquals(first.roomId, second.roomId)
            assertEquals(first.grid, second.grid)
            assertEquals(first.puzzleId, second.puzzleId)
            assertEquals(size, first.grid.size)
            assertEquals("", second.solution)
            assertTrue(second.seconds in 119..120)
            assertTrue(second.choices.isEmpty())
            val word = WordGrid.dictionary(catalog).first { WordGrid.findPath(first.grid, it) != null }
            val path = WordGrid.findPath(first.grid, word)!!
            assertEquals(0, service().answer(first.roomId, "Alice", word)!!.score)
            assertEquals(0, service().answer(first.roomId, "Alice", "", listOf(0, size + 1))!!.score)
            assertEquals(0, service().answer(first.roomId, "Alice", "", listOf(size - 1, size))!!.score)
            assertEquals(0, service().answer(first.roomId, "Alice", "", listOf(0, 1, 0))!!.score)
            val accepted = service().answer(first.roomId, "Alice", "ignored", path)!!
            assertEquals("playing", accepted.status)
            assertEquals(word.length, accepted.score)
            assertEquals(listOf(word), accepted.foundWords)
            assertFalse(accepted.answered)
            assertEquals("", accepted.solution)
            assertEquals(word.length, service().answer(first.roomId, "Alice", "", path)!!.score)
            assertEquals(word.length, service().mini(first.roomId, "Alice")!!.score)
            val bob = service().answer(first.roomId, "Bobby", "", path)!!
            assertEquals(word.length, bob.score)
            assertEquals(word.length, bob.opponentScore)
            assertNull(service().answer(first.roomId, "Outsider", "", path))
        }
        assertFailsWith<IllegalArgumentException> { service().joinMini("Alice", "word_puzzle", "bot", "5") }
    }

    @Test
    fun `grid timer resolves score winner draws and bot without early quiz termination`() {
        val dir = Files.createTempDirectory("grid-expiry").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val db = JsonDb(File(dir, "users.json")).load()
            val lobby = Lobby(ServerConfig.fromEnv(mapOf("DB" to "json", "DATA_DIR" to dir.path)), scope, db)
            fun service() = SocialService(dir.path, db, lobby)
            fun elapse(roomId: String, millis: Long) {
                val file = File(dir, "social.json")
                val root = Protocol.json.parseToJsonElement(file.readText()).jsonObject
                val rooms = root["rooms"]!!.jsonArray.map { value ->
                    if (value.jsonObject["id"]!!.jsonPrimitive.content != roomId) value else
                        JsonObject(value.jsonObject + ("startedAt" to JsonPrimitive(System.currentTimeMillis() - millis)))
                }
                file.writeText(JsonObject(root + ("rooms" to JsonArray(rooms))).toString())
            }
            val first = service().joinMini("Alice", "word_puzzle", "pvp", "4")
            service().joinMini("Bobby", "word_puzzle", "pvp", "4")
            val word = WordGrid.dictionary(catalog).first { WordGrid.findPath(first.grid, it) != null }
            val path = WordGrid.findPath(first.grid, word)!!
            service().answer(first.roomId, "Alice", "", path)
            elapse(first.roomId, 121_000)
            val ended = service().mini(first.roomId, "Alice")!!
            assertEquals("over", ended.status)
            assertEquals("Alice", ended.winner)
            assertTrue(word in ended.solution)
            assertEquals(ended.score, service().answer(first.roomId, "Bobby", "", path)!!.opponentScore)
            assertEquals("Alice", service().mini(first.roomId, "Alice")!!.winner)
            val draw = service().joinMini("Alice", "word_puzzle", "pvp", "6")
            service().joinMini("Bobby", "word_puzzle", "pvp", "6")
            elapse(draw.roomId, 121_000)
            assertNull(service().mini(draw.roomId, "Alice")!!.winner)
            val bot = service().joinMini("Alice", "word_puzzle", "bot", "8")
            elapse(bot.roomId, 65_000)
            val middle = service().mini(bot.roomId, "Alice")!!
            assertEquals("playing", middle.status)
            assertTrue(middle.opponentScore > 0)
            assertEquals("", middle.solution)
            elapse(bot.roomId, 121_000)
            assertEquals("Bot", service().mini(bot.roomId, "Alice")!!.winner)
        } finally { scope.cancel(); dir.deleteRecursively() }
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
