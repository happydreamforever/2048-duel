package com.duel2048.app.data

import android.content.Context
import android.content.SharedPreferences
import com.duel2048.app.net.LeaderboardClient
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.protocol.SavedGame
import com.duel2048.shared.protocol.SavedGameList
import com.duel2048.shared.protocol.SavedGameMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class GameSyncState(val syncing: Boolean = false, val pending: Int = 0,
    val offline: Boolean = false, val signedIn: Boolean = false, val conflicts: Set<String> = emptySet())

/** Local-first saves. Revisions prevent one device from silently replacing another device's progress. */
class GameSaves internal constructor(
    private val preferences: SharedPreferences,
    scope: CoroutineScope,
    private val transport: (suspend (String, String, SavedGame?) -> Pair<Int, String>)? = null,
) {
    constructor(context: Context, scope: CoroutineScope) : this(context.getSharedPreferences("game-saves", Context.MODE_PRIVATE), scope)
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private var owner = ""
    private var url = ""
    private var token = ""
    private var generation = 0L
    private val dirty = mutableSetOf<String>()
    private val conflicts = mutableMapOf<String, SavedGame>()
    private val _games = MutableStateFlow<List<SavedGame>>(emptyList())
    val games = _games.asStateFlow()
    private val _sync = MutableStateFlow(GameSyncState())
    val sync = _sync.asStateFlow()

    init { scope.launch { while (true) { delay(5000); if (dirty.isNotEmpty()) synchronize() } } }

    fun configure(server: String, authToken: String, account: String) {
        url = LeaderboardClient.httpBase(server)
        token = authToken
        val identity = "$url|${account.lowercase().ifBlank { "device" }}"
        if (identity != owner) {
            owner = identity
            generation++
            val stored = preferences.getString(key(), null)
            _games.value = stored?.let { runCatching { Protocol.json.decodeFromString(SavedGameList.serializer(), it).games }.getOrNull() }.orEmpty()
            dirty.clear()
            dirty.addAll(preferences.getStringSet(key() + "-pending", emptySet()).orEmpty())
            conflicts.clear()
        }
        publish()
    }

    private fun key(identity: String = owner): String = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Explicitly copy signed-out games into this account; never mix accounts automatically. */
    fun importDeviceGames(): Int {
        if (token.isBlank()) return 0
        val stored = preferences.getString(key("$url|device"), null) ?: return 0
        val deviceGames = runCatching { Protocol.json.decodeFromString(SavedGameList.serializer(), stored).games }.getOrDefault(emptyList())
        val existing = _games.value.map { it.id }.toSet()
        val imported = deviceGames.filter { !it.deleted && it.mode != SavedGameMode.ONLINE && it.id !in existing }
            .map { it.copy(revision = 0, updatedAt = System.currentTimeMillis()) }
        _games.value = (_games.value + imported).sortedByDescending { it.updatedAt }
        dirty.addAll(imported.map { it.id })
        persist()
        return imported.size
    }

    fun save(game: SavedGame) {
        if (game.mode == SavedGameMode.ONLINE || owner.isBlank()) return
        val old = _games.value.find { it.id == game.id }
        val next = game.copy(revision = old?.revision ?: 0, updatedAt = old?.updatedAt ?: 0)
        if (old == next || old?.deleted == true) return
        _games.value = (_games.value.filterNot { it.id == game.id } + next.copy(updatedAt = System.currentTimeMillis())).sortedByDescending { it.updatedAt }
        dirty.add(game.id)
        persist()
    }

    fun delete(id: String) { _games.value.find { it.id == id && it.mode != SavedGameMode.ONLINE }?.let { save(it.copy(deleted = true)) } }

    fun resolve(id: String, useCloud: Boolean) {
        val remote = conflicts.remove(id) ?: return
        if (useCloud) {
            _games.value = _games.value.filterNot { it.id == id } + remote
            dirty.remove(id)
        } else {
            _games.value = _games.value.map { if (it.id == id) it.copy(revision = remote.revision) else it }
            dirty.add(id)
        }
        persist()
    }

    private fun persist() {
        preferences.edit().putString(key(), Protocol.json.encodeToString(SavedGameList.serializer(), SavedGameList(_games.value)))
            .putStringSet(key() + "-pending", dirty.toSet()).apply()
        publish()
    }

    private fun publish(offline: Boolean = false, syncing: Boolean = false) {
        _sync.value = GameSyncState(syncing, dirty.size, offline, token.isNotBlank(), conflicts.keys.toSet())
    }

    suspend fun synchronize() = mutex.withLock {
        if (token.isBlank()) { publish(); return@withLock }
        val epoch = generation
        val target = url
        val auth = token
        publish(syncing = true)
        try {
            for (id in dirty.toList()) {
                if (id in conflicts) continue
                val sent = _games.value.find { it.id == id } ?: continue
                val (code, payload) = request(target, auth, sent)
                if (generation != epoch) return@withLock
                val received = Protocol.json.decodeFromString(SavedGame.serializer(), payload)
                if (code == 409) { conflicts[id] = received; continue }
                val current = _games.value.find { it.id == id }
                val unchanged = current == sent
                _games.value = _games.value.map { if (it.id != id) it else if (unchanged) received else it.copy(revision = received.revision) }
                if (unchanged) dirty.remove(id)
                persist()
            }
            val (_, body) = request(target, auth, null)
            if (generation != epoch) return@withLock
            val remote = Protocol.json.decodeFromString(SavedGameList.serializer(), body).games
            val merged = _games.value.associateBy { it.id }.toMutableMap()
            for (game in remote) {
                val local = merged[game.id]
                if (game.id !in dirty && (local == null || game.revision >= local.revision)) merged[game.id] = game
            }
            _games.value = merged.values.sortedByDescending { it.updatedAt }
            persist()
            publish()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (generation == epoch) publish(offline = true) }
    }

    private suspend fun request(base: String, auth: String, game: SavedGame?): Pair<Int, String> {
        transport?.let { return it(base, auth, game) }
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(base + "/games/saves").header("Authorization", "Bearer $auth")
            if (game != null) builder.post(Protocol.json.encodeToString(SavedGame.serializer(), game).toRequestBody("application/json".toMediaType()))
            http.newCall(builder.build()).execute().use { response ->
                check(response.isSuccessful || response.code == 409) { "Save sync failed (${response.code})" }
                response.code to response.body?.string().orEmpty()
            }
        }
    }
}
