package com.duel2048.server

import com.duel2048.server.db.AuthResult
import com.duel2048.server.db.JsonDb
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.social.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class PuzzleApiTest {
    @Test
    fun `HTTP match API sends questions without solutions and accepts tile selections`() = testApplication {
        val dir = Files.createTempDirectory("puzzle-api").toFile()
        val db = JsonDb(File(dir, "users.json")).load()
        val alice = (db.register("Alice", "pass1") as AuthResult.Ok).user
        application { module(ServerConfig.fromEnv(mapOf("DB" to "json", "DATA_DIR" to dir.path)), db) }
        val unauthenticated = client.post("/social/mini/join") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(MiniJoinBody.serializer(), MiniJoinBody(game = "math", mode = "bot")))
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        val mathResponse = client.post("/social/mini/join") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(MiniJoinBody.serializer(), MiniJoinBody(alice.token!!, "spoof", "math", "bot", "very_hard")))
        }
        assertEquals(HttpStatusCode.OK, mathResponse.status)
        val math = Protocol.json.decodeFromString(MiniRoomView.serializer(), mathResponse.bodyAsText())
        assertEquals("Alice", math.you)
        assertEquals(4, math.choices.size)
        assertEquals("", math.solution)
        val wordResponse = client.post("/social/mini/join") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(MiniJoinBody.serializer(), MiniJoinBody(alice.token!!, "spoof", "word", "bot", "5")))
        }
        val word = Protocol.json.decodeFromString(MiniRoomView.serializer(), wordResponse.bodyAsText())
        val solution = PuzzleCatalog("/datasets/matches").wordEntries(5).single { it.clue == word.prompt }.word
        assertEquals("", word.solution)
        assertFalse(word.puzzleId.contains(solution, ignoreCase = true))
        val tiles = word.grid.single().mapIndexed { i, c -> i to c }.toMutableList()
        val selected = solution.map { c -> tiles.first { it.second == c }.also { tiles.remove(it) }.first }
        val answer = client.post("/social/mini/answer") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(MiniAnswerBody.serializer(), MiniAnswerBody(word.roomId, "spoof", "", alice.token!!, selected)))
        }
        assertEquals(HttpStatusCode.OK, answer.status)
        val over = Protocol.json.decodeFromString(MiniRoomView.serializer(), answer.bodyAsText())
        assertEquals("Alice", over.winner)
        assertEquals(solution, over.solution)
        val gridResponse = client.post("/social/mini/join") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(MiniJoinBody.serializer(), MiniJoinBody(alice.token!!, "spoof", "word_puzzle", "bot", "4")))
        }
        assertEquals(HttpStatusCode.OK, gridResponse.status)
        val grid = Protocol.json.decodeFromString(MiniRoomView.serializer(), gridResponse.bodyAsText())
        assertEquals(4, grid.grid.size)
        assertEquals("", grid.solution)
        val dictionary = WordGrid.dictionary(PuzzleCatalog("/datasets/matches"))
        val gridWord = dictionary.first { WordGrid.findPath(grid.grid, it) != null }
        val gridPath = WordGrid.findPath(grid.grid, gridWord)!!
        val gridAnswer = client.post("/social/mini/answer") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(MiniAnswerBody.serializer(), MiniAnswerBody(grid.roomId, "spoof", "ignored", alice.token!!, gridPath)))
        }
        val scored = Protocol.json.decodeFromString(MiniRoomView.serializer(), gridAnswer.bodyAsText())
        assertEquals("Alice", scored.you)
        assertEquals(gridWord.length, scored.score)
        assertEquals(listOf(gridWord), scored.foundWords)
        assertEquals("playing", scored.status)
        assertEquals("", scored.solution)
        val privatePoll = client.get("/social/mini/${grid.roomId}?name=spoof") {
            header(HttpHeaders.Authorization, "Bearer ${alice.token}")
        }
        assertEquals(listOf(gridWord), Protocol.json.decodeFromString(MiniRoomView.serializer(), privatePoll.bodyAsText()).foundWords)
        val publicPoll = client.get("/social/mini/${grid.roomId}?name=Alice")
        assertTrue(Protocol.json.decodeFromString(MiniRoomView.serializer(), publicPoll.bodyAsText()).foundWords.isEmpty())
        val badGrid = client.post("/social/mini/join") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(MiniJoinBody.serializer(), MiniJoinBody(alice.token!!, "Alice", "word_puzzle", "bot", "5")))
        }
        assertEquals(HttpStatusCode.BadRequest, badGrid.status)
        val invalid = client.post("/social/mini/join") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(MiniJoinBody.serializer(), MiniJoinBody(alice.token!!, "Alice", "word", "bot", "90")))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
    }
}
