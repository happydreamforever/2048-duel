package com.duel2048.server.db

import com.duel2048.shared.protocol.AccountStats
import com.duel2048.shared.protocol.LeaderboardEntry
import kotlinx.serialization.Serializable

@Serializable
data class UserRecord(
    val id: String,
    val name: String,
    val nameLower: String,
    val passwordHash: String,
    val salt: String,
    val token: String? = null,
    val createdAt: Long,
    val lastLoginAt: Long = 0L,
    val stats: AccountStats = AccountStats(),
)

sealed interface AuthResult {
    data class Ok(val user: UserRecord) : AuthResult
    data class Failed(val code: String, val message: String) : AuthResult
}

/** One finished match, for the history table. */
data class MatchRecord(
    val matchId: String,
    val playedAt: Long,
    val durationMs: Long,
    val reason: String,
    val winnerName: String?,
    val p1Name: String,
    val p1UserId: String?,
    val p1Score: Int,
    val p2Name: String,
    val p2UserId: String?,
    val p2Score: Int,
)

/** Persistence used by the lobby. MySQL in production, JSON file for local development. */
interface UserStore {
    val description: String
    suspend fun userCount(): Int
    suspend fun register(name: String, password: String): AuthResult
    suspend fun login(name: String, password: String): AuthResult
    suspend fun authByToken(token: String): UserRecord?
    suspend fun logout(userId: String)
    suspend fun updateStats(userId: String, transform: (AccountStats) -> AccountStats): UserRecord?
    suspend fun leaderboard(limit: Int = 50): List<LeaderboardEntry>
    suspend fun names(): List<String> = emptyList()
    suspend fun recordMatch(match: MatchRecord) {}
    fun close() {}
}
