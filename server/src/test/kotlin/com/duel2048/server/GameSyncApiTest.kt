package com.duel2048.server

import com.duel2048.server.db.AuthResult
import com.duel2048.server.db.JsonDb
import com.duel2048.server.db.GameSaveStore
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.protocol.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class GameSyncApiTest {
    @Test
    fun `save API authenticates isolates accounts and returns conflicts`() = testApplication {
        val dir = Files.createTempDirectory("sync-api").toFile()
        val db = JsonDb(File(dir, "users.json")).load()
        val alice = (db.register("Alice", "pass1") as AuthResult.Ok).user
        val bob = (db.register("Bobby", "pass2") as AuthResult.Ok).user
        application { module(ServerConfig.fromEnv(mapOf("DB" to "json", "DATA_DIR" to dir.path)), db) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/games/saves").status)
        val game = SavedGame("cube-api", SavedGameType.CUBE, SavedGameMode.TRAINING, seed = 5, played = listOf("M"))
        val created = client.post("/games/saves") {
            header(HttpHeaders.Authorization, "Bearer ${alice.token}")
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(SavedGame.serializer(), game))
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val conflict = client.post("/games/saves") {
            header(HttpHeaders.Authorization, "Bearer ${alice.token}")
            setBody(Protocol.json.encodeToString(SavedGame.serializer(), game))
        }
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        val isolated = client.get("/games/saves") { header(HttpHeaders.Authorization, "Bearer ${bob.token}") }
        assertTrue(Protocol.json.decodeFromString(SavedGameList.serializer(), isolated.bodyAsText()).games.isEmpty())
        val forged = client.post("/games/saves") {
            header(HttpHeaders.Authorization, "Bearer ${alice.token}")
            setBody(Protocol.json.encodeToString(SavedGame.serializer(), game.copy(id = "forged", mode = SavedGameMode.ONLINE)))
        }
        assertEquals(HttpStatusCode.BadRequest, forged.status)
    }

    @Test
    fun `cube reconnect restores moves sequence and stable player identity`() = testApplication {
        val dir = Files.createTempDirectory("sync-reconnect").toFile()
        val db = JsonDb(File(dir, "users.json")).load()
        val alice = (db.register("Alice", "pass1") as AuthResult.Ok).user
        val config = ServerConfig.fromEnv(mapOf("DB" to "json", "DATA_DIR" to dir.path,
            "COUNTDOWN_SECONDS" to "0", "BOT_INTERVAL_MS" to "60000", "BOT_JITTER_MS" to "0"))
        application { module(config, db) }
        val sockets = createClient { install(WebSockets) }
        var matchId = ""
        var playerId = ""
        var fingerprint = ""
        sockets.webSocket("/ws") {
            next<Welcome>()
            send(Frame.Text(Protocol.encode(Auth(alice.token!!))))
            playerId = next<AuthOk>().playerId
            send(Frame.Text(Protocol.encode(FindMatch(MatchMode.BOT, GameType.CUBE_SOLVE))))
            matchId = next<CubeMatchFound>().matchId
            next<MatchStarted>()
            send(Frame.Text(Protocol.encode(CubeMoveMsg(matchId, 0, "M"))))
            fingerprint = next<CubeMoveAck>().fingerprint
            val durable = GameSaveStore(File(dir, "game-saves")).list(alice.id).single()
            assertEquals(listOf("M"), durable.played)
            assertEquals(1, durable.expectedSeq)
            close()
        }
        sockets.webSocket("/ws") {
            next<Welcome>()
            send(Frame.Text(Protocol.encode(Auth(alice.token!!))))
            next<AuthOk>()
            send(Frame.Text(Protocol.encode(ResumeMatch(matchId))))
            val restored = next<MatchRestored>().game
            assertEquals(playerId, restored.you?.id)
            assertEquals(1, restored.expectedSeq)
            assertEquals(listOf("M"), restored.played)
            val cube = com.duel2048.shared.cube.CubeScramble.apply(restored.seed)
                .apply(com.duel2048.shared.cube.CubeMove.M)
            assertEquals(fingerprint, cube.fingerprint())
            send(Frame.Text(Protocol.encode(CubeMoveMsg(matchId, 1, "E'"))))
            assertEquals(1, next<CubeMoveAck>().seq)
            close()
        }
    }

    @Test
    fun `2048 reconnect restores both complete boards and prevents another account taking the seat`() = testApplication {
        val dir = Files.createTempDirectory("tiles-reconnect").toFile()
        val db = JsonDb(File(dir, "users.json")).load()
        val alice = (db.register("Alice", "pass1") as AuthResult.Ok).user
        val bob = (db.register("Bobby", "pass2") as AuthResult.Ok).user
        val config = ServerConfig.fromEnv(mapOf("DB" to "json", "DATA_DIR" to dir.path,
            "COUNTDOWN_SECONDS" to "0", "BOT_INTERVAL_MS" to "60000", "BOT_JITTER_MS" to "0"))
        application { module(config, db) }
        val sockets = createClient { install(WebSockets) }
        var matchId = ""
        var boardKey = ""
        sockets.webSocket("/ws") {
            next<Welcome>()
            send(Frame.Text(Protocol.encode(Auth(alice.token!!))))
            next<AuthOk>()
            send(Frame.Text(Protocol.encode(FindMatch(MatchMode.BOT))))
            matchId = next<MatchFound>().matchId
            next<MatchStarted>()
            send(Frame.Text(Protocol.encode(MoveMsg(matchId, 0, Direction.LEFT))))
            boardKey = next<MoveAck>().state.syncKey()
            close()
        }
        sockets.webSocket("/ws") {
            next<Welcome>()
            send(Frame.Text(Protocol.encode(Auth(bob.token!!))))
            next<AuthOk>()
            send(Frame.Text(Protocol.encode(ResumeMatch(matchId))))
            assertEquals("match_unavailable", next<ErrorMsg>().code)
            close()
        }
        sockets.webSocket("/ws") {
            next<Welcome>()
            send(Frame.Text(Protocol.encode(Auth(alice.token!!))))
            next<AuthOk>()
            send(Frame.Text(Protocol.encode(ResumeMatch(matchId))))
            val restored = next<MatchRestored>().game
            assertEquals(boardKey, restored.board!!.syncKey())
            assertNotNull(restored.opponentBoard)
            assertEquals(1, restored.expectedSeq)
            assertTrue(restored.remainingMs > 0)
            val durable = GameSaveStore(File(dir, "game-saves")).list(alice.id).single()
            assertEquals(boardKey, durable.board!!.syncKey())
            close()
        }
    }

    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.next(): T = withTimeout(10_000) {
        var message: T? = null
        while (message == null) {
            val frame = incoming.receive()
            if (frame is Frame.Text) {
                val decoded = Protocol.decodeServer(frame.readText())
                if (decoded is ErrorMsg && decoded !is T) error("${decoded.code}: ${decoded.message}")
                if (decoded is T) message = decoded
            }
        }
        message
    }
}
