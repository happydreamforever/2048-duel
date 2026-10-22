package com.duel2048.server

import com.duel2048.server.db.UserStore
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.social.AdminNote
import com.duel2048.shared.social.ChatLine
import com.duel2048.shared.social.LiveGame
import com.duel2048.shared.social.MiniRoomView
import com.duel2048.shared.social.PlayerPresence
import com.duel2048.shared.social.Puzzles
import com.duel2048.shared.social.WalletView
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Social side channel: presence is read from the live lobby, while admin notes, reports,
 * chat, wallets, and minigame rooms live in a JSON file. A poll loop re-reads that file
 * so an admin can drop a notification on disk or via POST /admin/notify without a broker.
 */
class SocialService(
    dataDir: String,
    private val db: UserStore,
    private val lobby: Lobby,
) {
    private val log = LoggerFactory.getLogger("Social")
    private val file = File(dataDir, "social.json")
    private val json = Protocol.json
    private val lock = Any()
    private var state = SocialFile()
    private var seenNote = 0L
    private val rooms = ConcurrentHashMap<String, MiniRoom>()

    init {
        file.parentFile?.mkdirs()
        reload()
        state.rooms.forEach { saved -> rooms[saved.id] = MiniRoom(saved.id, saved.game, saved.mode, saved.difficulty, saved.host, saved) }
        seenNote = state.notes.maxOfOrNull { it.id } ?: 0L
    }

    suspend fun pollLoop() {
        while (true) {
            delay(3_000)
            reload()
            val fresh = state.notes.filter { it.id > seenNote }
            if (fresh.isNotEmpty()) {
                seenNote = fresh.maxOf { it.id }
                fresh.forEach { log.info("admin notification #${it.id}: ${it.text}") }
            }
        }
    }

    suspend fun players(): List<PlayerPresence> {
        val online = lobby.onlineNames()
        val known = LinkedHashSet<String>()
        known += db.names()
        known += online
        return known.filter { it.isNotBlank() && it != "Player" }
            .sortedWith(compareByDescending<String> { it in online }.thenBy { it.lowercase() })
            .map { PlayerPresence(it, it in online) }
    }

    fun notifications(since: Long): List<AdminNote> = synchronized(lock) { state.notes.filter { it.id > since } }

    fun live(): List<LiveGame> = synchronized(lock) {
        var changed = false
        for (room in rooms.values) {
            val before = room.status
            room.view(room.host)
            if (room.status != before) changed = true
        }
        if (changed) saveRooms()
        lobby.liveGames() + rooms.values.filter { it.status == "playing" }.map { it.live() }
    }

    fun addNote(text: String): AdminNote = synchronized(lock) {
        val note = AdminNote((state.notes.maxOfOrNull { it.id } ?: 0L) + 1, text.trim().take(280), System.currentTimeMillis())
        state = state.copy(notes = state.notes + note)
        save()
        note
    }

    fun report(name: String, text: String, game: String) = synchronized(lock) {
        state = state.copy(reports = state.reports + ReportRow(name, text.take(400), game, System.currentTimeMillis()))
        save()
        log.info("report from $name ($game): $text")
    }

    fun chat(name: String, text: String, room: String): ChatLine? {
        val allowed = com.duel2048.shared.social.ChatPhrases.all
        if (text !in allowed) return null
        val line = ChatLine(System.currentTimeMillis(), name.ifBlank { "Player" }, text, room.ifBlank { "lobby" })
        synchronized(lock) {
            state = state.copy(chat = (state.chat + line).takeLast(200))
            save()
        }
        return line
    }

    fun chatSince(room: String, since: Long): List<ChatLine> =
        synchronized(lock) { state.chat.filter { it.room == room && it.at > since } }

    suspend fun wallet(token: String): WalletView? {
        val user = db.authByToken(token) ?: return null
        return synchronized(lock) { state.wallets[user.id] ?: WalletView() }
    }

    suspend fun applyWallet(token: String, scoreDelta: Int, coinDelta: Int): WalletView? {
        val user = db.authByToken(token) ?: return null
        return synchronized(lock) {
            val cur = state.wallets[user.id] ?: WalletView()
            val next = WalletView(
                score = (cur.score + scoreDelta).coerceAtLeast(0),
                coins = (cur.coins + coinDelta).coerceAtLeast(0),
            )
            state = state.copy(wallets = state.wallets + (user.id to next))
            save()
            next
        }
    }

    fun joinMini(name: String, game: String, mode: String, difficulty: String): MiniRoomView {
        synchronized(lock) {
        require(game == "math" || game == "word") { "Unknown game" }
        val who = name.ifBlank { "Player" }
        if (mode == "watch") {
            val live = rooms.values.firstOrNull { it.game == game && it.status == "playing" }
            if (live != null) {
                live.spectators++
                return live.view(who)
            }
            return MiniRoomView("", game, "watch", difficulty, "", "empty", who, "", seconds = 0)
        }
        val waiting = rooms.values.firstOrNull { it.game == game && it.difficulty == difficulty && it.mode == "pvp" && it.status == "waiting" && it.host != who }
        if (mode == "pvp" && waiting != null) {
            waiting.opponent = who
            waiting.status = "playing"
            waiting.startedAt = System.currentTimeMillis()
            saveRooms()
            return waiting.view(who)
        }
        val id = UUID.randomUUID().toString().substring(0, 8)
        val room = MiniRoom(id, game, mode, difficulty, who)
        rooms[id] = room
        if (mode != "pvp") room.status = "playing"
        if (room.status == "playing") room.startedAt = System.currentTimeMillis()
        saveRooms()
        return room.view(who)
        }
    }

    fun mini(roomId: String, name: String): MiniRoomView? = synchronized(lock) {
        val room = rooms[roomId] ?: return@synchronized null
        val before = room.status
        val view = room.view(name)
        if (room.status != before) saveRooms()
        view
    }

    fun answer(roomId: String, name: String, answer: String): MiniRoomView? {
        synchronized(lock) {
        val room = rooms[roomId] ?: return null
        if (name != room.host && name != room.opponent) return null
        room.view(name)
        if (room.status != "playing") { saveRooms(); return room.view(name) }
        if (answer.trim().equals(room.solution, ignoreCase = true)) {
            room.status = "over"
            room.winner = name
        }
        saveRooms()
        return room.view(name)
        }
    }

    private fun saveRooms() {
        state = state.copy(rooms = rooms.values.map { it.checkpoint() })
        save()
    }

    private fun reload() = synchronized(lock) {
        if (!file.exists()) return@synchronized
        try {
            val loaded = json.decodeFromString(SocialFile.serializer(), file.readText())
            state = loaded
        } catch (e: Exception) {
            log.warn("social file unreadable: ${e.message}")
        }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = java.nio.file.Files.createTempFile(file.parentFile.toPath(), "social-", ".tmp")
        try {
            java.io.FileOutputStream(tmp.toFile()).use { out ->
                out.write(json.encodeToString(SocialFile.serializer(), state).toByteArray())
                out.fd.sync()
            }
            try { java.nio.file.Files.move(tmp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(tmp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { java.nio.file.Files.deleteIfExists(tmp) }
    }

    private class MiniRoom(
        val id: String,
        val game: String,
        val mode: String,
        val difficulty: String,
        val host: String,
        saved: MiniRoomState? = null,
    ) {
        var opponent: String = saved?.opponent ?: if (mode == "pvp") "" else "Bot"
        var status: String = saved?.status ?: "waiting"
        var winner: String? = saved?.winner
        var startedAt: Long = saved?.startedAt ?: 0
        var spectators: Int = 0
        val prompt: String
        val solution: String
        val grid: List<String>
        val seconds: Int

        init {
            if (saved != null) {
                grid = saved.grid; solution = saved.solution; prompt = saved.prompt; seconds = saved.seconds
            } else if (game == "word") {
                val size = if (difficulty == "6") 6 else 4
                val (g, word, secs) = Puzzles.word(size, Random.Default)
                grid = g
                solution = word
                prompt = "Find the word"
                seconds = secs
            } else {
                val (text, answer) = Puzzles.math(difficulty, Random.Default)
                prompt = text
                solution = answer
                grid = emptyList()
                seconds = when (difficulty) {
                    "very_hard" -> 90
                    "hard" -> 40
                    "medium" -> 30
                    else -> 20
                }
            }
        }

        fun checkpoint() = MiniRoomState(id, game, mode, difficulty, host, opponent, status, winner,
            prompt, solution, grid, seconds, startedAt)

        fun view(you: String): MiniRoomView {
            val remaining = if (startedAt > 0) (seconds - (System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(0) else seconds
            if (status == "playing" && remaining == 0) status = "over"
            return MiniRoomView(
            roomId = id,
            game = game,
            mode = mode,
            difficulty = difficulty,
            prompt = prompt,
            status = status,
            you = you,
            opponent = (if (you == opponent) host else opponent).ifBlank { "waiting" },
            winner = winner,
            solution = if (status == "over") solution else "",
            grid = grid,
            seconds = remaining,
        )
        }

        fun live() = LiveGame(id, game, host, 0, opponent.ifBlank { "…" }, spectators)
    }
}

@Serializable
private data class ReportRow(val name: String, val text: String, val game: String, val at: Long)

@Serializable
private data class SocialFile(
    val notes: List<AdminNote> = emptyList(),
    val reports: List<ReportRow> = emptyList(),
    val chat: List<ChatLine> = emptyList(),
    val wallets: Map<String, WalletView> = emptyMap(),
    val rooms: List<MiniRoomState> = emptyList(),
)

@Serializable
private data class MiniRoomState(val id: String, val game: String, val mode: String, val difficulty: String,
    val host: String, val opponent: String, val status: String, val winner: String?, val prompt: String,
    val solution: String, val grid: List<String>, val seconds: Int, val startedAt: Long)
