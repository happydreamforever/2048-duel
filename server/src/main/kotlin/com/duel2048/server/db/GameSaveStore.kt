package com.duel2048.server.db

import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.protocol.SavedGame
import com.duel2048.shared.protocol.SavedGameList
import com.duel2048.shared.protocol.SavedGameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class SaveConflict(val current: SavedGame) : IllegalStateException("A newer save already exists")

/** Account-isolated atomic checkpoints, independent of the account database backend. */
class GameSaveStore(private val directory: File, private val now: () -> Long = System::currentTimeMillis, private val sql: MySqlStore? = null) {
    private val mutex = Mutex()

    private fun file(userId: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$key.json")
    }

    private fun read(userId: String): List<SavedGame> {
        if(sql!=null) return sql.connection { c -> c.prepareStatement("SELECT payload FROM game_saves WHERE user_id=?").use { ps ->
            ps.setString(1,userId);ps.executeQuery().use { rs -> buildList { while(rs.next()) add(Protocol.json.decodeFromString(SavedGame.serializer(),rs.getString(1))) } }
        } }
        return file(userId).let {
        if (it.isFile) Protocol.json.decodeFromString(SavedGameList.serializer(), it.readText()).games else emptyList()
    }

    }

    suspend fun list(userId: String): List<SavedGame> = withContext(Dispatchers.IO) {
        mutex.withLock { read(userId).sortedByDescending { it.updatedAt } }
    }

    suspend fun put(userId: String, game: SavedGame, authoritative: Boolean = false): SavedGame = withContext(Dispatchers.IO) {
        game.validate()
        require(authoritative || game.mode != SavedGameMode.ONLINE) { "Online state is server-owned" }
        mutex.withLock {
            val games = read(userId)
            val current = games.find { it.id == game.id }
            require(current == null || current.mode == game.mode && current.game == game.game) { "Save identity cannot change" }
            if (authoritative && current != null && game.revision <= current.revision) return@withLock current
            if (!authoritative && game.revision != (current?.revision ?: 0L)) {
                throw SaveConflict(current ?: game.copy(revision = 0))
            }
            require(current != null || games.size < 500) { "Save limit reached" }
            val saved = game.copy(revision = if (authoritative) game.revision else game.revision + 1, updatedAt = now())
            if(sql!=null) {
                sql.connection { c -> c.prepareStatement("INSERT INTO game_saves(user_id,save_id,revision,game,mode,completed,deleted,payload,updated_at) VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE revision=VALUES(revision),completed=VALUES(completed),deleted=VALUES(deleted),payload=VALUES(payload),updated_at=VALUES(updated_at)").use { ps ->
                    val args=listOf(userId,saved.id,saved.revision,saved.game.name,saved.mode.name,saved.completed,saved.deleted,Protocol.json.encodeToString(SavedGame.serializer(),saved),saved.updatedAt)
                    args.forEachIndexed { i,v -> ps.setObject(i+1,v) };ps.executeUpdate()
                } }
                return@withLock saved
            }
            directory.mkdirs()
            val destination = file(userId).toPath()
            val temporary = Files.createTempFile(directory.toPath(), "checkpoint-", ".tmp")
            try {
                java.io.FileOutputStream(temporary.toFile()).use { out ->
                    out.write(Protocol.json.encodeToString(SavedGameList.serializer(), SavedGameList(games.filterNot { it.id == saved.id } + saved)).toByteArray())
                    out.fd.sync()
                }
                try {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { Files.deleteIfExists(temporary) }
            saved
        }
    }
}
