package com.duel2048.server.db

import com.duel2048.shared.protocol.AccountStats
import com.duel2048.shared.protocol.Accounts
import com.duel2048.shared.protocol.LeaderboardEntry
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLIntegrityConstraintViolationException
import java.util.UUID

/**
 * MySQL/MariaDB-backed store (plain JDBC through a HikariCP pool). Tables are created on first start.
 * JDBC calls block, so every access runs on Dispatchers.IO.
 */
class MySqlStore(jdbcUrl: String, user: String, password: String) : UserStore {

    private val log = LoggerFactory.getLogger("MySqlStore")
    private val pool = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = user
            this.password = password
            maximumPoolSize = 8
            minimumIdle = 1
            connectionTimeout = 10_000
            poolName = "duel2048"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "64")
        },
    )

    override val description: String = (if (jdbcUrl.startsWith("jdbc:mariadb:")) "MariaDB " else "MySQL ") + jdbcUrl

    init {
        pool.connection.use { c ->
            c.createStatement().use { st ->
                st.execute(USERS_DDL)
                st.execute(MATCHES_DDL)
            }
        }
        log.info("connected to $jdbcUrl, schema ready")
    }

    private suspend fun <T> db(block: (Connection) -> T): T = withContext(Dispatchers.IO) { pool.connection.use(block) }

    override suspend fun userCount(): Int = db { c ->
        c.prepareStatement("SELECT COUNT(*) FROM users").use { ps -> ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) } }
    }

    override suspend fun register(name: String, password: String): AuthResult {
        val trimmed = name.trim()
        Accounts.validateName(trimmed)?.let { return AuthResult.Failed(it, "Name must be ${Accounts.NAME_MIN}-${Accounts.NAME_MAX} letters, digits or _") }
        Accounts.validatePassword(password)?.let { return AuthResult.Failed(it, "Password must have at least ${Accounts.PASSWORD_MIN} characters") }
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
        return db { c ->
            try {
                c.prepareStatement(
                    "INSERT INTO users (id, name, name_lower, password_hash, salt, token, created_at, last_login_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { ps ->
                    ps.setString(1, user.id); ps.setString(2, user.name); ps.setString(3, user.nameLower)
                    ps.setString(4, user.passwordHash); ps.setString(5, user.salt); ps.setString(6, user.token)
                    ps.setLong(7, user.createdAt); ps.setLong(8, user.lastLoginAt)
                    ps.executeUpdate()
                }
                log.info("registered ${user.name} (${user.id})")
                AuthResult.Ok(user)
            } catch (e: SQLIntegrityConstraintViolationException) {
                AuthResult.Failed("name_taken", "That name is already taken")
            }
        }
    }

    override suspend fun login(name: String, password: String): AuthResult = db { c ->
        val user = selectWhere(c, "name_lower = ?", name.trim().lowercase())
            ?: return@db AuthResult.Failed("bad_credentials", "Wrong name or password")
        if (!Passwords.verify(password, user.salt, user.passwordHash)) return@db AuthResult.Failed("bad_credentials", "Wrong name or password")
        val token = Passwords.newToken()
        val now = System.currentTimeMillis()
        c.prepareStatement("UPDATE users SET token = ?, last_login_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, token); ps.setLong(2, now); ps.setString(3, user.id); ps.executeUpdate()
        }
        AuthResult.Ok(user.copy(token = token, lastLoginAt = now))
    }

    override suspend fun authByToken(token: String): UserRecord? = db { c -> selectWhere(c, "token = ?", token) }

    override suspend fun logout(userId: String) = db { c ->
        c.prepareStatement("UPDATE users SET token = NULL WHERE id = ?").use { ps -> ps.setString(1, userId); ps.executeUpdate() }
        Unit
    }

    override suspend fun updateStats(userId: String, transform: (AccountStats) -> AccountStats): UserRecord? = db { c ->
        c.autoCommit = false
        try {
            val user = selectWhere(c, "id = ? FOR UPDATE", userId) ?: run { c.rollback(); return@db null }
            val st = transform(user.stats)
            c.prepareStatement(
                "UPDATE users SET wins = ?, losses = ?, draws = ?, matches = ?, best_score = ?, best_tile = ?, garbage_sent = ? WHERE id = ?",
            ).use { ps ->
                ps.setInt(1, st.wins); ps.setInt(2, st.losses); ps.setInt(3, st.draws); ps.setInt(4, st.matches)
                ps.setInt(5, st.bestScore); ps.setInt(6, st.bestTile); ps.setInt(7, st.garbageSent); ps.setString(8, userId)
                ps.executeUpdate()
            }
            c.commit()
            user.copy(stats = st)
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    override suspend fun leaderboard(limit: Int): List<LeaderboardEntry> = db { c ->
        c.prepareStatement(
            "SELECT name, wins, losses, draws, matches, best_score, best_tile FROM users ORDER BY wins DESC, best_score DESC, name ASC LIMIT ?",
        ).use { ps ->
            ps.setInt(1, limit.coerceIn(1, 100))
            ps.executeQuery().use { rs ->
                val out = ArrayList<LeaderboardEntry>()
                while (rs.next()) {
                    out += LeaderboardEntry(
                        name = rs.getString("name"), wins = rs.getInt("wins"), losses = rs.getInt("losses"),
                        bestScore = rs.getInt("best_score"), bestTile = rs.getInt("best_tile"),
                        draws = rs.getInt("draws"), matches = rs.getInt("matches"),
                    )
                }
                out
            }
        }
    }

    override suspend fun recordMatch(match: MatchRecord) = db { c ->
        c.prepareStatement(
            "INSERT INTO match_history (match_id, played_at, duration_ms, reason, winner_name, p1_name, p1_user_id, p1_score, p2_name, p2_user_id, p2_score) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { ps ->
            ps.setString(1, match.matchId); ps.setLong(2, match.playedAt); ps.setLong(3, match.durationMs); ps.setString(4, match.reason)
            ps.setString(5, match.winnerName); ps.setString(6, match.p1Name); ps.setString(7, match.p1UserId); ps.setInt(8, match.p1Score)
            ps.setString(9, match.p2Name); ps.setString(10, match.p2UserId); ps.setInt(11, match.p2Score)
            ps.executeUpdate()
        }
        Unit
    }

    override fun close() = pool.close()

    private fun selectWhere(c: Connection, where: String, arg: String): UserRecord? =
        c.prepareStatement("SELECT $COLUMNS FROM users WHERE $where").use { ps ->
            ps.setString(1, arg)
            ps.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
        }

    private fun map(rs: ResultSet) = UserRecord(
        id = rs.getString("id"),
        name = rs.getString("name"),
        nameLower = rs.getString("name_lower"),
        passwordHash = rs.getString("password_hash"),
        salt = rs.getString("salt"),
        token = rs.getString("token"),
        createdAt = rs.getLong("created_at"),
        lastLoginAt = rs.getLong("last_login_at"),
        stats = AccountStats(
            wins = rs.getInt("wins"), losses = rs.getInt("losses"), draws = rs.getInt("draws"), matches = rs.getInt("matches"),
            bestScore = rs.getInt("best_score"), bestTile = rs.getInt("best_tile"), garbageSent = rs.getInt("garbage_sent"),
        ),
    )

    companion object {
        private const val COLUMNS = "id, name, name_lower, password_hash, salt, token, created_at, last_login_at, wins, losses, draws, matches, best_score, best_tile, garbage_sent"

        val USERS_DDL = """
            CREATE TABLE IF NOT EXISTS users (
              id            VARCHAR(16)  NOT NULL PRIMARY KEY,
              name          VARCHAR(16)  NOT NULL,
              name_lower    VARCHAR(16)  NOT NULL,
              password_hash VARCHAR(64)  NOT NULL,
              salt          VARCHAR(32)  NOT NULL,
              token         VARCHAR(64)  NULL,
              created_at    BIGINT       NOT NULL,
              last_login_at BIGINT       NOT NULL DEFAULT 0,
              wins          INT          NOT NULL DEFAULT 0,
              losses        INT          NOT NULL DEFAULT 0,
              draws         INT          NOT NULL DEFAULT 0,
              matches       INT          NOT NULL DEFAULT 0,
              best_score    INT          NOT NULL DEFAULT 0,
              best_tile     INT          NOT NULL DEFAULT 0,
              garbage_sent  INT          NOT NULL DEFAULT 0,
              UNIQUE KEY uk_name_lower (name_lower),
              UNIQUE KEY uk_token (token),
              KEY idx_leaderboard (wins DESC, best_score DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """.trimIndent()

        val MATCHES_DDL = """
            CREATE TABLE IF NOT EXISTS match_history (
              id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
              match_id    VARCHAR(16)  NOT NULL,
              played_at   BIGINT       NOT NULL,
              duration_ms BIGINT       NOT NULL,
              reason      VARCHAR(16)  NOT NULL,
              winner_name VARCHAR(16)  NULL,
              p1_name     VARCHAR(16)  NOT NULL,
              p1_user_id  VARCHAR(16)  NULL,
              p1_score    INT          NOT NULL,
              p2_name     VARCHAR(16)  NOT NULL,
              p2_user_id  VARCHAR(16)  NULL,
              p2_score    INT          NOT NULL,
              KEY idx_played_at (played_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """.trimIndent()
    }
}
