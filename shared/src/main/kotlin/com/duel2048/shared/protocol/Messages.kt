package com.duel2048.shared.protocol

import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.engine.GarbagePlacement
import com.duel2048.shared.engine.MoveEvents
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchMode { PVP, BOT }

@Serializable
enum class EndReason { BOARD_FULL, TIME_UP, FORFEIT }

@Serializable
data class PlayerInfo(val id: String, val name: String, val isBot: Boolean = false)

/** Per-account statistics kept by the server. */
@Serializable
data class AccountStats(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val matches: Int = 0,
    val bestScore: Int = 0,
    val bestTile: Int = 0,
    val garbageSent: Int = 0,
)

@Serializable
data class LeaderboardEntry(
    val name: String,
    val wins: Int,
    val losses: Int,
    val bestScore: Int,
    val bestTile: Int,
    val draws: Int = 0,
    val matches: Int = 0,
)

/** Validation rules shared by client and server. Functions return an error code or null. */
object Accounts {
    const val NAME_MIN = 3
    const val NAME_MAX = 16
    const val PASSWORD_MIN = 4
    const val PASSWORD_MAX = 64
    private val nameRegex = Regex("^[\\p{L}\\p{N}_]+$")

    fun validateName(name: String): String? {
        val n = name.trim()
        return if (n.length !in NAME_MIN..NAME_MAX || !nameRegex.matches(n)) "invalid_name" else null
    }

    fun validatePassword(password: String): String? =
        if (password.length !in PASSWORD_MIN..PASSWORD_MAX) "invalid_password" else null
}

@Serializable
data class PlayerResult(
    val playerId: String,
    val score: Int,
    val maxTile: Int,
    val moves: Int,
    val merges: Int,
    val bestCombo: Int,
    val garbageSent: Int,
    val garbageReceived: Int,
)

// ---------------------------------------------------------------- client -> server

@Serializable
sealed interface ClientMessage

@Serializable
@SerialName("hello")
data class Hello(val name: String, val clientVersion: Int = Protocol.VERSION) : ClientMessage

@Serializable
@SerialName("register")
data class Register(val name: String, val password: String) : ClientMessage

@Serializable
@SerialName("login")
data class Login(val name: String, val password: String) : ClientMessage

/** Resume a session with the token returned by a previous login. */
@Serializable
@SerialName("auth")
data class Auth(val token: String) : ClientMessage

@Serializable
@SerialName("logout")
data object Logout : ClientMessage

@Serializable
@SerialName("find_match")
data class FindMatch(val mode: MatchMode = MatchMode.PVP) : ClientMessage

@Serializable
@SerialName("cancel_find")
data object CancelFindMatch : ClientMessage

@Serializable
@SerialName("move")
data class MoveMsg(val matchId: String, val seq: Int, val direction: Direction) : ClientMessage

@Serializable
@SerialName("leave")
data class LeaveMatch(val matchId: String) : ClientMessage

@Serializable
@SerialName("ping")
data class Ping(val clientTime: Long) : ClientMessage

// ---------------------------------------------------------------- server -> client

@Serializable
sealed interface ServerMessage

@Serializable
@SerialName("welcome")
data class Welcome(val playerId: String, val serverVersion: Int, val onlinePlayers: Int, val registeredUsers: Int = 0) : ServerMessage

@Serializable
@SerialName("auth_ok")
data class AuthOk(val playerId: String, val name: String, val token: String, val stats: AccountStats) : ServerMessage

/** codes: name_taken, invalid_name, invalid_password, bad_credentials, bad_token */
@Serializable
@SerialName("auth_failed")
data class AuthFailed(val code: String, val message: String) : ServerMessage

/** Sent after every finished match to logged-in players. */
@Serializable
@SerialName("stats")
data class StatsUpdated(val stats: AccountStats) : ServerMessage

@Serializable
@SerialName("queued")
data class Queued(val position: Int, val botFallbackMs: Long) : ServerMessage

@Serializable
@SerialName("match_found")
data class MatchFound(
    val matchId: String,
    val you: PlayerInfo,
    val opponent: PlayerInfo,
    val seed: Long,
    val boardSize: Int,
    val durationMs: Long,
    val countdownSeconds: Int,
    val yourState: GameState,
    val opponentState: GameState,
) : ServerMessage

@Serializable
@SerialName("countdown")
data class Countdown(val matchId: String, val secondsLeft: Int) : ServerMessage

@Serializable
@SerialName("match_started")
data class MatchStarted(val matchId: String, val remainingMs: Long) : ServerMessage

@Serializable
@SerialName("tick")
data class Tick(val matchId: String, val remainingMs: Long) : ServerMessage

/** Authoritative result of the client's own move [seq]. */
@Serializable
@SerialName("move_ack")
data class MoveAck(val matchId: String, val seq: Int, val state: GameState, val events: MoveEvents) : ServerMessage

/** The move was not applied; [state] is the authoritative state to resync to. */
@Serializable
@SerialName("move_rejected")
data class MoveRejected(val matchId: String, val seq: Int, val expectedSeq: Int, val reason: String, val state: GameState) : ServerMessage

@Serializable
@SerialName("opponent_moved")
data class OpponentMoved(val matchId: String, val state: GameState, val events: MoveEvents) : ServerMessage

/** Garbage landed on [targetId]'s board. Sent to both players. */
@Serializable
@SerialName("garbage")
data class GarbageLanded(
    val matchId: String,
    val targetId: String,
    val fromId: String,
    val state: GameState,
    val placements: List<GarbagePlacement>,
) : ServerMessage

@Serializable
@SerialName("match_over")
data class MatchOver(
    val matchId: String,
    val winnerId: String?,
    val reason: EndReason,
    val results: List<PlayerResult>,
) : ServerMessage

@Serializable
@SerialName("error")
data class ErrorMsg(val code: String, val message: String) : ServerMessage

@Serializable
@SerialName("pong")
data class Pong(val clientTime: Long, val serverTime: Long) : ServerMessage

@Serializable
data class ServerStats(val onlinePlayers: Int, val queued: Int, val activeMatches: Int, val totalMatches: Int)
