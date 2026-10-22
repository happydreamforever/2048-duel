package com.duel2048.app.data

import android.content.SharedPreferences
import com.duel2048.shared.protocol.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class GameSavesTest {
    private fun cube() = SavedGame("cube-save", SavedGameType.CUBE, SavedGameMode.TRAINING, seed = 6, played = listOf("M"))
    private fun json(game: SavedGame) = Protocol.json.encodeToString(SavedGame.serializer(), game)
    private fun json(games: List<SavedGame>) = Protocol.json.encodeToString(SavedGameList.serializer(), SavedGameList(games))

    @Test
    fun `offline edits and pending uploads survive repository recreation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val preferences = memoryPreferences()
            val saves = GameSaves(preferences, scope) { _, _, _ -> error("offline") }
            saves.configure("ws://example.test/ws", "alice-token", "Alice")
            saves.save(cube())
            saves.synchronize()
            assertTrue(saves.sync.value.offline)
            val restored = GameSaves(preferences, scope) { _, _, game ->
                if (game == null) 200 to json(emptyList()) else 200 to json(game.copy(revision = 1))
            }
            restored.configure("ws://example.test/ws", "alice-token", "Alice")
            assertEquals(1, restored.sync.value.pending)
            assertEquals(cube().played, restored.games.value.single().played)
            restored.synchronize()
            assertEquals(0, restored.sync.value.pending)
            assertEquals(1L, restored.games.value.single().revision)
        } finally { scope.cancel() }
    }

    @Test
    fun `progress made during an upload stays queued at the acknowledged revision`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val uploaded = CompletableDeferred<SavedGame>()
            val release = CompletableDeferred<Unit>()
            var cloud: SavedGame? = null
            val saves = GameSaves(memoryPreferences(), scope) { _, _, game ->
                if (game == null) 200 to json(listOfNotNull(cloud)) else {
                    if (!uploaded.isCompleted) { uploaded.complete(game); release.await() }
                    cloud = game.copy(revision = game.revision + 1)
                    200 to json(cloud!!)
                }
            }
            saves.configure("ws://example.test/ws", "token", "Alice")
            saves.save(cube())
            val upload = launch { saves.synchronize() }
            uploaded.await()
            saves.save(cube().copy(played = listOf("M", "E'")))
            release.complete(Unit)
            upload.join()
            assertEquals(listOf("M", "E'"), saves.games.value.single().played)
            assertEquals(1L, saves.games.value.single().revision)
            assertEquals(1, saves.sync.value.pending)
            saves.synchronize()
            assertEquals(0, saves.sync.value.pending)
            assertEquals(saves.games.value.single().played, cloud!!.played)
        } finally { scope.cancel() }
    }

    @Test
    fun `conflicts require a choice and cannot silently resurrect a cloud deletion`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var cloud = cube().copy(revision = 3, deleted = true)
            val saves = GameSaves(memoryPreferences(), scope) { _, _, game ->
                when {
                    game == null -> 200 to json(listOf(cloud))
                    game.revision != cloud.revision -> 409 to json(cloud)
                    else -> { cloud = game.copy(revision = cloud.revision + 1); 200 to json(cloud) }
                }
            }
            saves.configure("ws://example.test/ws", "token", "Alice")
            saves.save(cube())
            saves.synchronize()
            assertTrue(cube().id in saves.sync.value.conflicts)
            assertFalse(saves.games.value.single().deleted)
            saves.resolve(cube().id, useCloud = true)
            assertTrue(saves.games.value.single().deleted)
            assertEquals(0, saves.sync.value.pending)
            assertTrue(saves.sync.value.conflicts.isEmpty())
        } finally { scope.cancel() }
    }

    @Test
    fun `account changes discard late responses and device imports are explicit`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val saves = GameSaves(memoryPreferences(), scope) { _, _, game ->
                entered.complete(Unit)
                release.await()
                200 to json(game!!.copy(revision = 1))
            }
            saves.configure("ws://example.test/ws", "", "")
            saves.save(cube())
            saves.configure("ws://example.test/ws", "alice-token", "Alice")
            assertTrue(saves.games.value.isEmpty())
            assertEquals(1, saves.importDeviceGames())
            val upload = launch { saves.synchronize() }
            entered.await()
            saves.configure("ws://example.test/ws", "bob-token", "Bobby")
            release.complete(Unit)
            upload.join()
            assertTrue(saves.games.value.isEmpty())
            saves.configure("ws://example.test/ws", "alice-token", "Alice")
            assertEquals(1, saves.sync.value.pending)
            assertEquals(0L, saves.games.value.single().revision)
        } finally { scope.cancel() }
    }

    /** Only the SharedPreferences operations used by this repository; no Android runtime needed. */
    private fun memoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        val changes = mutableMapOf<String, Any?>()
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString", "putStringSet" -> { changes[args!![0] as String] = args[1]; proxy }
                "apply", "commit" -> { values.putAll(changes); changes.clear(); method.name == "commit" }
                else -> error("Unexpected editor operation ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString", "getStringSet" -> values[args!![0]] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences operation ${method.name}")
            }
        } as SharedPreferences
    }
}
