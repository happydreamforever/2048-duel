package com.duel2048.server

import com.duel2048.server.db.JsonDb
import com.duel2048.shared.protocol.Protocol
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class MiniPersistenceTest {
    @Test
    fun `puzzle rooms keep their prompt participants and result across restarts`() {
        val dir = Files.createTempDirectory("mini-rooms").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val db = JsonDb(File(dir, "users.json")).load()
            val lobby = Lobby(ServerConfig.fromEnv(mapOf("DB" to "json", "DATA_DIR" to dir.path)), scope, db)
            fun service() = SocialService(dir.path, db, lobby)
            val first = service()
            val waiting = first.joinMini("Alice", "math", "pvp", "easy")
            assertEquals("waiting", waiting.status)
            val joined = service().joinMini("Bobby", "math", "pvp", "easy")
            assertEquals(waiting.roomId, joined.roomId)
            assertEquals(waiting.prompt, joined.prompt)
            assertEquals(waiting.choices, joined.choices)
            assertEquals(waiting.puzzleId, joined.puzzleId)
            assertEquals(4, joined.choices.size)
            assertEquals("playing", joined.status)
            assertEquals("Alice", joined.opponent)
            val restarted = service()
            assertEquals("Bobby", restarted.mini(waiting.roomId, "Alice")!!.opponent)
            assertNull(restarted.answer(waiting.roomId, "Outsider", "1"))
            val parts = waiting.prompt.split(" ")
            val a = parts[0].toBigInteger()
            val b = parts[2].toBigInteger()
            val solution = when (parts[1]) { "+" -> a + b; "-" -> a - b; "*" -> a * b; else -> a / b }
            val over = restarted.answer(waiting.roomId, "Bobby", solution.toString())!!
            assertEquals("Bobby", over.winner)
            assertEquals("over", service().mini(waiting.roomId, "Alice")!!.status)
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test
    fun `expired puzzle timer stays expired after restart`() {
        val dir = Files.createTempDirectory("mini-expiry").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val db = JsonDb(File(dir, "users.json")).load()
            val lobby = Lobby(ServerConfig.fromEnv(mapOf("DB" to "json", "DATA_DIR" to dir.path)), scope, db)
            val room = SocialService(dir.path, db, lobby).joinMini("Alice", "word", "bot", "6")
            val file = File(dir, "social.json")
            val json = Protocol.json.parseToJsonElement(file.readText()).jsonObject
            val rooms = json["rooms"]!!.jsonArray.map { value ->
                JsonObject(value.jsonObject + ("startedAt" to JsonPrimitive(System.currentTimeMillis() - 600_000)))
            }
            file.writeText(JsonObject(json + ("rooms" to JsonArray(rooms))).toString())
            val restored = SocialService(dir.path, db, lobby).mini(room.roomId, "Alice")!!
            assertEquals(room.grid, restored.grid)
            assertEquals("over", restored.status)
            assertEquals(0, restored.seconds)
            assertNotNull(restored.solution)
        } finally { scope.cancel(); dir.deleteRecursively() }
    }
}
