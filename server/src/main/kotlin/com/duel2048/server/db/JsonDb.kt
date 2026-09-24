package com.duel2048.server.db

import com.duel2048.shared.protocol.AccountStats
import com.duel2048.shared.protocol.Accounts
import com.duel2048.shared.protocol.LeaderboardEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
data class DbFile(val version: Int = 1, val users: List<UserRecord> = emptyList())

/**
 * Tiny JSON-file database: everything lives in memory and the whole file is rewritten
 * atomically (temp file + rename) after each change. Good enough for a hobby server;
 * swap for a real database when the user list grows.
 */
class JsonDb(val file: File) : UserStore {

    private val log = LoggerFactory.getLogger("JsonDb")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val users = LinkedHashMap<String, UserRecord>()
    private val idByName = HashMap<String, String>()
    private val idByToken = HashMap<String, String>()

    fun load(): JsonDb {
        if (file.exists()) {
            val db = json.decodeFromString(DbFile.serializer(), file.readText())
            db.users.forEach { index(it) }
            log.info("loaded ${users.size} users from ${file.absolutePath}")
        } else {
            log.info("no database yet; it will be created at ${file.absolutePath}")
        }
        return this
    }

    override val description: String get() = "JSON file ${file.absolutePath}"

    override suspend fun userCount(): Int = mutex.withLock { users.size }

    override suspend fun register(name: String, password: String): AuthResult = mutex.withLock {
        val trimmed = name.trim()
        Accounts.validateName(trimmed)?.let { return AuthResult.Failed(it, "Name must be ${Accounts.NAME_MIN}-${Accounts.NAME_MAX} letters, digits or _") }
        Accounts.validatePassword(password)?.let { return AuthResult.Failed(it, "Password must have at least ${Accounts.PASSWORD_MIN} characters") }
        if (idByName.containsKey(trimmed.lowercase())) return AuthResult.Failed("name_taken", "That name is already taken")
        val now = System.currentTimeMillis()
        val salt = Passwords.newSalt()
        val user = UserRecord(
            id = "u" + UUID.randomUUID().toString().substring(0, 8),
            name = trimmed,
            nameLower = trimmed.lowercase(),
            passwordHash = Passwords.hash(password, salt),
            salt = salt,
            token = Passwords.newToken(),
            createdAt = now,
            lastLoginAt = now,
        )
        index(user)
        saveLocked()
        log.info("registered ${user.name} (${user.id}), users=${users.size}")
        AuthResult.Ok(user)
    }

    override suspend fun login(name: String, password: String): AuthResult = mutex.withLock {
        val id = idByName[name.trim().lowercase()] ?: return AuthResult.Failed("bad_credentials", "Wrong name or password")
        val user = users[id] ?: return AuthResult.Failed("bad_credentials", "Wrong name or password")
        if (!Passwords.verify(password, user.salt, user.passwordHash)) return AuthResult.Failed("bad_credentials", "Wrong name or password")
        val updated = user.copy(token = Passwords.newToken(), lastLoginAt = System.currentTimeMillis())
        replace(user, updated)
        saveLocked()
        AuthResult.Ok(updated)
    }

    override suspend fun authByToken(token: String): UserRecord? = mutex.withLock {
        idByToken[token]?.let { users[it] }
    }

    override suspend fun logout(userId: String) {
        mutex.withLock {
            val user = users[userId] ?: return
            replace(user, user.copy(token = null))
            saveLocked()
        }
    }

    override suspend fun updateStats(userId: String, transform: (AccountStats) -> AccountStats): UserRecord? = mutex.withLock {
        val user = users[userId] ?: return@withLock null
        val updated = user.copy(stats = transform(user.stats))
        replace(user, updated)
        saveLocked()
        updated
    }

    override suspend fun names(): List<String> = mutex.withLock { users.values.map { it.name } }

    override suspend fun leaderboard(limit: Int): List<LeaderboardEntry> = mutex.withLock {
        users.values
            .sortedWith(compareByDescending<UserRecord> { it.stats.wins }.thenByDescending { it.stats.bestScore })
            .take(limit)
            .map { LeaderboardEntry(it.name, it.stats.wins, it.stats.losses, it.stats.bestScore, it.stats.bestTile) }
    }

    private fun index(user: UserRecord) {
        users[user.id] = user
        idByName[user.nameLower] = user.id
        user.token?.let { idByToken[it] = user.id }
    }

    private fun replace(old: UserRecord, new: UserRecord) {
        old.token?.let { idByToken.remove(it) }
        index(new)
    }

    private fun saveLocked() {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.absolutePath + ".tmp")
        tmp.writeText(json.encodeToString(DbFile.serializer(), DbFile(users = users.values.toList())))
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
