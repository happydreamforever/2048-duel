package com.duel2048.server

import com.duel2048.server.db.GameSaveStore
import com.duel2048.server.db.SaveConflict
import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeScramble
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.protocol.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class GameSaveStoreTest {
    private fun cube(id: String = "cube-save") = SavedGame(id, SavedGameType.CUBE, SavedGameMode.TRAINING,
        seed = 42, played = listOf("M", "E'", "R"))

    @Test
    fun `cube history and tile RNG survive a fresh store`() = runBlocking {
        val dir = Files.createTempDirectory("game-save-reload").toFile()
        try {
            val store = GameSaveStore(dir)
            val saved = store.put("alice", cube())
            val board = GameEngine.move(GameEngine.newGame(7), Direction.LEFT).state
            store.put("alice", SavedGame("tiles-save", SavedGameType.TILES, SavedGameMode.SOLO, board = board))
            val reloaded = GameSaveStore(dir).list("alice")
            val restored = reloaded.single { it.id == saved.id }
            assertEquals(saved, restored)
            assertEquals(CubeScramble.apply(42).applyAll(cube().played.map { CubeMove.parse(it)!! }).fingerprint(),
                CubeScramble.apply(restored.seed).applyAll(restored.played.map { CubeMove.parse(it)!! }).fingerprint())
            val next = reloaded.single { it.id == "tiles-save" }.board!!
            assertEquals(GameEngine.move(board, Direction.DOWN).state, GameEngine.move(next, Direction.DOWN).state)
            assertTrue(GameSaveStore(dir).list("bob").isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `stale and simultaneous writes cannot overwrite newer progress`() = runBlocking {
        val dir = Files.createTempDirectory("game-save-conflict").toFile()
        try {
            val store = GameSaveStore(dir)
            val first = store.put("alice", cube())
            val attempts = coroutineScope { List(2) { n -> async {
                runCatching { store.put("alice", first.copy(played = first.played + if (n == 0) "U" else "F")) }
            } }.map { it.await() } }
            assertEquals(1, attempts.count { it.isSuccess })
            assertIs<SaveConflict>(attempts.single { it.isFailure }.exceptionOrNull())
            val current = store.list("alice").single()
            assertEquals(2L, current.revision)
            assertFailsWith<SaveConflict> { store.put("alice", first) }
            assertEquals(current, GameSaveStore(dir).list("alice").single())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `deletion tombstone survives reload and rejects stale resurrection`() = runBlocking {
        val dir = Files.createTempDirectory("game-save-delete").toFile()
        try {
            val store = GameSaveStore(dir)
            val original = store.put("alice", cube())
            val deleted = store.put("alice", original.copy(deleted = true))
            assertTrue(GameSaveStore(dir).list("alice").single().deleted)
            assertFailsWith<SaveConflict> { store.put("alice", original) }
            assertEquals(deleted, store.list("alice").single())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `online checkpoints are server owned and out of order jobs are ignored`() = runBlocking {
        val dir = Files.createTempDirectory("game-save-online").toFile()
        try {
            val store = GameSaveStore(dir)
            val online = cube().copy(mode = SavedGameMode.ONLINE, revision = 2)
            assertFailsWith<IllegalArgumentException> { store.put("alice", online) }
            store.put("alice", online, authoritative = true)
            store.put("alice", online.copy(revision = 1, played = emptyList()), authoritative = true)
            assertEquals(online.played, store.list("alice").single().played)
            assertFailsWith<IllegalArgumentException> { store.put("alice", cube("../outside")) }
            assertFailsWith<IllegalArgumentException> { store.put("alice", cube().copy(played = listOf("BAD"))) }
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `corrupted storage is reported without replacing it`() = runBlocking {
        val dir = Files.createTempDirectory("game-save-corrupt").toFile()
        try {
            val store = GameSaveStore(dir)
            store.put("alice", cube())
            val file = dir.listFiles()!!.single()
            file.writeText("corrupted")
            assertFails { store.list("alice") }
            assertFails { store.put("alice", cube("another")) }
            assertEquals("corrupted", file.readText())
        } finally { dir.deleteRecursively() }
    }
}
