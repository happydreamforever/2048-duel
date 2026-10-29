package com.duel2048.server

import com.duel2048.server.db.UserStore
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.social.AdminNote
import com.duel2048.shared.social.ChatLine
import com.duel2048.shared.social.LiveGame
import com.duel2048.shared.social.MiniRoomView
import com.duel2048.shared.social.PlayerPresence
import com.duel2048.shared.social.WordGrid
import com.duel2048.shared.social.Puzzles
import com.duel2048.shared.social.PuzzleCatalog
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
    private val puzzles = PuzzleCatalog("/datasets/matches")
    private val dictionary by lazy { WordGrid.dictionary(puzzles) }

    init {
        file.parentFile?.mkdirs()
        reload()
        val sqlRooms = lobby.economy.rooms().filter { it.mode != "demo" && it.game in listOf("math","word","word_puzzle") }
        val restored = if (db is com.duel2048.server.db.MySqlStore && sqlRooms.isNotEmpty()) sqlRooms.mapNotNull { row ->
            try { json.decodeFromString(MiniRoomState.serializer(),row.payload) }
            catch(e:Exception) {
                if(row.status in listOf("waiting","playing")) throw IllegalStateException("Active room ${row.id} has an invalid checkpoint",e)
                log.warn("Historical room ${row.id} has metadata only; keeping its SQL history")
                null
            }
        } else state.rooms
        restored.forEach { saved -> rooms[saved.id] = MiniRoom(saved.id, saved.game, saved.mode, saved.difficulty, saved.host, saved) }
        seenNote = state.notes.maxOfOrNull { it.id } ?: 0L
        demoRooms().forEach { demo -> lobby.economy.checkpoint(demo.id,demo.game,"demo","playing",demo.playerA,demo.playerB,demo.scoreA,demo.scoreB,null,"{}") }
    }

    suspend fun pollLoop() {
        while (true) {
            delay(3_000)
            reload()
            synchronized(lock) {
                var changed=false
                rooms.values.forEach { room -> val before=room.status;room.view(room.host);if(before!=room.status) changed=true }
                if(changed) saveRooms()
            }
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

    fun notifications(since: Long): List<AdminNote> = synchronized(lock) {
        val published = if(db is com.duel2048.server.db.MySqlStore) db.connection { c ->
            c.prepareStatement("SELECT id,text,updated_at FROM announcements WHERE status='published' ORDER BY updated_at DESC,id DESC LIMIT 30").use { ps ->
                ps.executeQuery().use { rs -> buildList { while(rs.next()) add(AdminNote(1_000_000_000L+rs.getLong(1),rs.getString(2),rs.getLong(3))) } }
            }
        } else emptyList()
        (state.notes+published).filter { it.id>since }.sortedBy { it.at }.takeLast(30)
    }

    fun live(): List<LiveGame> = synchronized(lock) {
        var changed = false
        for (room in rooms.values) {
            val before = room.status
            room.view(room.host)
            if (room.status != before) changed = true
        }
        if (changed) saveRooms()
        demoRooms() + lobby.liveGames() + rooms.values.filter { it.status == "playing" && it.privateChallengeId.isBlank() }.map { it.live() }
    }

    fun addNote(text: String): AdminNote = synchronized(lock) {
        val note = AdminNote((state.notes.maxOfOrNull { it.id } ?: 0L) + 1, text.trim().take(280), System.currentTimeMillis())
        state = state.copy(notes = state.notes + note)
        save()
        note
    }

    fun report(name: String, text: String, game: String) = synchronized(lock) {
        if(db is com.duel2048.server.db.MySqlStore) {
            db.connection { c ->
                c.prepareStatement("INSERT INTO reports(player_name,game,message,created_at,updated_at) VALUES (?,?,?,?,?)").use { ps ->
                    ps.setString(1,name);ps.setString(2,game);ps.setString(3,text);ps.setLong(4,System.currentTimeMillis());ps.setLong(5,System.currentTimeMillis());ps.executeUpdate()
                }
            }
        } else {
            state=state.copy(reports=state.reports+ReportRow(name,text.take(400),game,System.currentTimeMillis()))
            save()
        }
        log.info("support report received from $name ($game)")
    }

    fun canChat(userId: String, roomId: String): Boolean {
        if(lobby.canChat(userId,roomId)) return true
        return synchronized(lock) {
            val room=rooms[roomId] ?: return@synchronized false
            val before=room.status
            room.view(room.host)
            if(before!=room.status) saveRooms()
            room.mode=="pvp" && room.status=="playing" && userId in room.users.values
        }
    }

    fun chat(user: com.duel2048.server.db.UserRecord, text: String, room: String): ChatLine? {
        if(text !in com.duel2048.shared.social.ChatPhrases.all || !canChat(user.id,room)) return null
        val line=ChatLine(System.currentTimeMillis(),user.name,text,room)
        synchronized(lock) {
            if(db is com.duel2048.server.db.MySqlStore) db.connection { c ->
                c.prepareStatement("INSERT INTO chat_messages(player_name,message,room,created_at) VALUES (?,?,?,?)").use { ps ->
                    ps.setString(1,user.name);ps.setString(2,text);ps.setString(3,room);ps.setLong(4,line.at);ps.executeUpdate()
                }
            } else {
                state=state.copy(chat=(state.chat+line).takeLast(200));save()
            }
        }
        return line
    }

    fun chatSince(room: String, since: Long): List<ChatLine> = synchronized(lock) {
        if(db is com.duel2048.server.db.MySqlStore) db.connection { c ->
            c.prepareStatement("SELECT player_name,message,room,created_at FROM chat_messages WHERE room=? AND created_at>? ORDER BY id DESC LIMIT 50").use { ps ->
                ps.setString(1,room);ps.setLong(2,since);ps.executeQuery().use { rs -> buildList {
                    while(rs.next()) add(ChatLine(rs.getLong(4),rs.getString(1),rs.getString(2),rs.getString(3)))
                }.reversed() }
            }
        } else state.chat.filter { it.room==room && it.at>since }.takeLast(50)
    }

    private fun demoRooms(): List<LiveGame> {
        val tick = System.currentTimeMillis()/1000
        return listOf("2048","cube","math","word","word_puzzle").map { game ->
            val room = LiveGame("demo-$game",game,"Demo_A",((tick%120)/3).toInt(),"Demo_B",((tick%120)/4).toInt(),true)
            room
        }
    }

    fun watchFrame(id: String): com.duel2048.shared.social.WatchFrame? {
        val room = live().firstOrNull { it.id==id } ?: return null
        val tick = System.currentTimeMillis()/1000
        val grid = if(room.demo) when(room.game) {
            "word_puzzle" -> listOf("CATS","DOGS","BIRD","FISH")
            "word" -> listOf("CATS")
            "2048" -> listOf("2482","4248","8824","2442")
            else -> emptyList()
        } else synchronized(lock) { rooms[id]?.grid.orEmpty() }
        if(room.demo) lobby.economy.checkpoint(room.id,room.game,"demo","playing",room.playerA,room.playerB,room.scoreA,room.scoreB,null,"{}")
        return com.duel2048.shared.social.WatchFrame(room,tick,System.currentTimeMillis(),room.demo,grid,
            if(room.demo) (0..(tick%4).toInt()).toList() else emptyList(), if(room.demo) when(room.game) { "cube" -> "Demo cube move ${listOf("U","R","F","L")[(tick%4).toInt()]}"; "math" -> "Demo answer / score update"; else -> "Demo move / score update" } else "Live score update",120-(tick%120).toInt())
    }

    suspend fun wallet(token: String): WalletView? = db.authByToken(token)?.let { lobby.economy.wallet(it.id) }

    suspend fun purchase(token: String, id: String, coins: Int): com.duel2048.shared.social.CoinPurchase? =
        db.authByToken(token)?.let { lobby.economy.purchase(it.id,id,coins) }

    fun joinMini(name: String, game: String, mode: String, difficulty: String, userId: String = ""): MiniRoomView {
        synchronized(lock) {
        require(game in listOf("math", "word", "word_puzzle")) { "Unknown game" }
        require(mode in listOf("pvp", "bot", "watch")) { "Training is played locally" }
        require(if (game == "math") difficulty in Puzzles.mathLevels else if (game == "word_puzzle") difficulty.toIntOrNull() in WordGrid.sizes else difficulty.toIntOrNull() in Puzzles.wordLengths) { "Unknown difficulty" }
        val who = name.ifBlank { "Player" }
        if (mode == "watch") {
            val live = rooms.values.firstOrNull { it.game == game && it.status == "playing" }
            if (live != null) {
                live.spectators++
                return live.view(who)
            }
            return MiniRoomView("", game, "watch", difficulty, "", "empty", who, "", seconds = 0)
        }
        rooms.values.forEach { it.view(who) }
        rooms.values.firstOrNull { it.game==game && it.mode==mode && it.difficulty==difficulty && it.status in listOf("waiting","playing") && (it.host==who || it.opponent==who) }?.let { return it.view(who) }
        val waiting = rooms.values.firstOrNull { it.game == game && it.difficulty == difficulty && it.mode == "pvp" && it.status == "waiting" && it.privateChallengeId.isBlank() && it.host != who }
        if (mode == "pvp" && waiting != null) {
            val participants = waiting.users + if(userId.isNotBlank()) mapOf(who to userId) else emptyMap()
            lobby.economy.enter(waiting.id,game,mode,participants.values.toList())
            waiting.users.putAll(participants)
            waiting.opponent = who
            waiting.status = "playing"
            waiting.startedAt = System.currentTimeMillis()
            saveRooms()
            return waiting.view(who)
        }
        val id = UUID.randomUUID().toString().substring(0, 8)
        val room = MiniRoom(id, game, mode, difficulty, who)
        if(userId.isNotBlank()) room.users[who]=userId
        if(mode=="bot") lobby.economy.enter(id,game,mode,room.users.values.toList())
        rooms[id] = room
        if (mode != "pvp") room.status = "playing"
        if (room.status == "playing") room.startedAt = System.currentTimeMillis()
        saveRooms()
        return room.view(who)
        }
    }

    fun userBusy(userId:String):Boolean = synchronized(lock) {
        rooms.values.any { room -> room.view(room.host); userId in room.users.values && room.status in listOf("waiting","playing") }
    }
    fun canReadMini(id:String,userId:String?):Boolean = synchronized(lock) {
        rooms[id]?.let { it.privateChallengeId.isBlank() || userId in it.users.values } ?: true
    }
    fun privateRoom(id:String,challengeId:String):MiniRoomView? = synchronized(lock) {
        rooms[id]?.takeIf { it.privateChallengeId==challengeId }?.let { it.view(it.host) }
    }
    fun createPrivate(id:String,game:String,difficulty:String,from:String,fromId:String,to:String,toId:String,challengeId:String):MiniRoomView = synchronized(lock) {
        rooms[id]?.let { existing ->
            require(existing.privateChallengeId==challengeId && existing.users[from]==fromId && existing.users[to]==toId) { "Room identity mismatch" }
            return@synchronized existing.view(from)
        }
        require(game in listOf("math","word","word_puzzle") && fromId!=toId) { "Invalid private game" }
        require(!userBusy(fromId) && !userBusy(toId)) { "A player is already in another competition" }
        val room=MiniRoom(id,game,"pvp",difficulty,from)
        room.opponent=to;room.users[from]=fromId;room.users[to]=toId;room.privateChallengeId=challengeId
        lobby.economy.enter(id,game,"pvp",listOf(fromId,toId))
        room.status="playing";room.startedAt=System.currentTimeMillis()+3_000
        rooms[id]=room;saveRooms()
        room.view(from)
    }

    fun mini(roomId: String, name: String): MiniRoomView? = synchronized(lock) {
        val room = rooms[roomId] ?: return@synchronized null
        val before = room.status
        val view = room.view(name)
        if (room.status != before) saveRooms()
        view
    }

    fun answer(roomId: String, name: String, answer: String, selectedLetters: List<Int> = emptyList()): MiniRoomView? {
        synchronized(lock) {
        val room = rooms[roomId] ?: return null
        if (name != room.host && name != room.opponent) return null
        room.view(name)
        if (room.status != "playing") { saveRooms(); return room.view(name) }
        if(room.privateChallengeId.isNotBlank() && System.currentTimeMillis()<room.startedAt) return room.view(name)
        if (room.game == "word_puzzle") {
            val word = WordGrid.selectedWord(room.grid, selectedLetters) ?: return room.view(name)
            if (word in dictionary) {
                val found = room.foundWords.getOrPut(name) { mutableSetOf() }
                if (found.add(word)) saveRooms()
            }
            return room.view(name)
        }
        if (name in room.incorrectPlayers) return room.view(name)
        val submitted = if (room.game == "word") Puzzles.selectedWord(room.grid, selectedLetters) ?: return room.view(name)
            else answer.trim().takeIf { it in room.choices } ?: return room.view(name)
        if (room.game == "word" && selectedLetters.size != room.solution.length) return room.view(name)
        if (submitted.equals(room.solution, ignoreCase = true)) {
            room.status = "over"
            room.winner = name
        } else {
            room.incorrectPlayers.add(name)
            if (room.mode == "bot") room.winner = room.opponent
            if (room.mode != "pvp" || room.host in room.incorrectPlayers && room.opponent in room.incorrectPlayers) room.status = "over"
        }
        saveRooms()
        return room.view(name)
        }
    }

    private fun saveRooms() {
        state = state.copy(rooms = rooms.values.map { it.checkpoint() })
        rooms.values.forEach { room ->
            lobby.economy.checkpoint(room.id,room.game,room.mode,room.status,room.host,room.opponent,
                room.score(room.host),room.score(room.opponent),room.winner,
                json.encodeToString(MiniRoomState.serializer(),room.checkpoint()))
        }
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

    private inner class MiniRoom(
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
        var privateChallengeId: String = saved?.privateChallengeId.orEmpty()
        val prompt: String
        val solution: String
        val grid: List<String>
        val seconds: Int
        val choices: List<String>
        val puzzleId: String
        val users = saved?.users.orEmpty().toMutableMap()
        val incorrectPlayers = saved?.incorrectPlayers.orEmpty().toMutableSet()
        val foundWords = saved?.foundWords.orEmpty().mapValues { it.value.toMutableSet() }.toMutableMap()

        init {
            if (saved != null) {
                grid = saved.grid; solution = saved.solution; prompt = saved.prompt; seconds = saved.seconds
                choices = if (game == "math" && saved.choices.isEmpty()) Puzzles.choicesFor(saved.solution) else saved.choices
                puzzleId = saved.puzzleId
            } else if (game == "word_puzzle") {
                grid = WordGrid.generate(difficulty.toInt(), dictionary)
                solution = ""
                prompt = "Find words by connecting letters horizontally or vertically."
                seconds = WordGrid.seconds
                choices = emptyList()
                puzzleId = UUID.randomUUID().toString()
            } else if (game == "word") {
                val word = puzzles.word(difficulty.toInt(), Random.Default)
                grid = word.grid
                solution = word.answer
                prompt = word.prompt
                seconds = word.seconds
                choices = emptyList()
                puzzleId = word.id
            } else {
                val problem = puzzles.math(difficulty, Random.Default)
                prompt = problem.prompt
                solution = problem.answer
                choices = problem.choices
                puzzleId = problem.id
                grid = emptyList()
                seconds = when (difficulty) {
                    "very_hard" -> 90
                    "hard" -> 40
                    "medium" -> 30
                    else -> 20
                }
            }
        }

        private val possibleWords by lazy { dictionary.filter { WordGrid.findPath(grid, it) != null }.sorted() }

        fun checkpoint() = MiniRoomState(id, game, mode, difficulty, host, opponent, status, winner,
            prompt, solution, grid, seconds, startedAt, choices, puzzleId, incorrectPlayers.toList(), foundWords.mapValues { it.value.toList() },users.toMap(),privateChallengeId)

        fun view(you: String): MiniRoomView {
            val remaining = if (startedAt > 0) (seconds - (System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(0) else seconds
            if (game == "word_puzzle" && status == "playing") {
                if (mode == "bot") foundWords[opponent] = possibleWords.take(minOf((possibleWords.size / 2).coerceAtLeast(1), ((seconds - remaining) / 20).coerceAtLeast(0))).toMutableSet()
                if (remaining == 0) {
                    status = "over"
                    val hostScore = WordGrid.score(foundWords[host].orEmpty())
                    val otherScore = WordGrid.score(foundWords[opponent].orEmpty())
                    winner = when { hostScore > otherScore -> host; otherScore > hostScore -> opponent; else -> null }
                }
            }
            if (status == "playing" && remaining == 0) status = "over"
            if (status == "playing" && game != "word_puzzle" && mode == "bot" && remaining <= seconds / 2) {
                status = "over"
                winner = opponent
            }
            if(status=="over") lobby.economy.settle(id,winner?.let { users[it] },winner==null)
            val starting=privateChallengeId.isNotBlank() && status=="playing" && startedAt>System.currentTimeMillis()
            return MiniRoomView(
            roomId = id,
            game = game,
            mode = mode,
            difficulty = difficulty,
            prompt = if(starting) "" else prompt,
            status = if(starting) "waiting" else status,
            you = you,
            opponent = (if (you == opponent) host else opponent).ifBlank { "waiting" },
            winner = winner,
            solution = if (status == "over") { if (game == "word_puzzle") possibleWords.joinToString(", ") else solution } else "",
            grid = if(starting) emptyList() else grid,
            seconds = if(starting) ((startedAt-System.currentTimeMillis()+999)/1000).toInt().coerceAtLeast(1) else remaining,
            choices = if(starting) emptyList() else choices,
            puzzleId = puzzleId,
            answered = you in incorrectPlayers,
            foundWords = foundWords[you].orEmpty().toList().sorted(),
            score = score(you),
            opponentScore = score(if (you == opponent) host else opponent),
        )
        }

        fun score(name:String):Int = if(game=="word_puzzle") WordGrid.score(foundWords[name].orEmpty()) else if(status=="over" && winner==name) 1 else 0
        fun live() = LiveGame(id, game, host, score(host), opponent.ifBlank { "…" }, score(opponent))
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
    val solution: String, val grid: List<String>, val seconds: Int, val startedAt: Long,
    val choices: List<String> = emptyList(), val puzzleId: String = "", val incorrectPlayers: List<String> = emptyList(), val foundWords: Map<String, List<String>> = emptyMap(), val users:Map<String,String> = emptyMap(), val privateChallengeId:String = "")
