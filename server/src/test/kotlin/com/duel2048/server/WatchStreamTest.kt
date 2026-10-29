package com.duel2048.server

import com.duel2048.server.db.JsonDb
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.social.WatchFrame
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.io.File
import kotlin.test.*

class WatchStreamTest {
    @Test fun `demo watch room pushes changing server signals without another player`() = testApplication {
        val dir=Files.createTempDirectory("watch-signals").toFile()
        val db=JsonDb(File(dir,"users.json")).load()
        application { module(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),db) }
        val ws=createClient { install(WebSockets) }
        ws.webSocket("/social/watch/demo-word_puzzle") {
            val first=Protocol.json.decodeFromString(WatchFrame.serializer(),(withTimeout(5000) { incoming.receive() } as Frame.Text).readText())
            val next=Protocol.json.decodeFromString(WatchFrame.serializer(),(withTimeout(5000) { incoming.receive() } as Frame.Text).readText())
            assertTrue(first.demo)
            assertTrue(next.sequence>first.sequence)
            assertTrue(next.serverTime>first.serverTime)
            assertEquals(4,next.grid.size)
            assertEquals("demo-word_puzzle",next.room.id)
            close()
        }
    }
}
