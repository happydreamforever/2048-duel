package com.duel2048.shared.social

import kotlinx.serialization.Serializable

/** Coins to enter, score and coins awarded when a match ends. */
object Economy {
    const val START_COINS = 100

    fun level(score: Int): Int = 1 + (score.coerceAtLeast(0) / 100)

    fun joinCost(mode: String): Int = when (mode) {
        "pvp" -> 10
        "bot" -> 5
        else -> 0
    }

    /** Coins and score granted after a finished game. */
    fun payout(mode: String, won: Boolean): Pair<Int, Int> {
        val playScore = 5
        return when (mode) {
            "pvp" -> if (won) 25 to playScore + 20 else 0 to playScore
            "bot" -> if (won) 12 to playScore + 10 else 0 to playScore
            "train" -> 0 to playScore
            else -> 0 to 0
        }
    }
}

object ChatPhrases {
    val all = listOf(
        "Good luck", "Nice", "Wow", "Hurry up", "GG", "Thanks", "Oops", "Close one",
        "🔥", "😂", "👍", "👏", "😎", "🤝", "💀", "✨",
    )
}

@Serializable
data class PlayerPresence(val name: String, val online: Boolean)

@Serializable
data class AdminNote(val id: Long, val text: String, val at: Long)

@Serializable
data class LiveGame(
    val id: String,
    val game: String,
    val playerA: String,
    val scoreA: Int,
    val playerB: String,
    val scoreB: Int,
)

@Serializable
data class ChatLine(val at: Long, val from: String, val text: String, val room: String)

@Serializable
data class WalletView(val score: Int = 0, val coins: Int = Economy.START_COINS)

@Serializable
data class WalletBody(val token: String, val scoreDelta: Int = 0, val coinDelta: Int = 0)

@Serializable
data class ReportBody(val token: String = "", val name: String = "", val text: String, val game: String = "")

@Serializable
data class ChatBody(val token: String = "", val name: String = "", val text: String, val room: String = "lobby")

@Serializable
data class NotifyBody(val text: String)

@Serializable
data class MiniJoinBody(val token: String = "", val name: String = "", val game: String, val mode: String, val difficulty: String = "easy")

@Serializable
data class MiniAnswerBody(val roomId: String, val name: String, val answer: String, val token: String = "", val selectedLetters: List<Int> = emptyList())

@Serializable
data class MiniRoomView(
    val roomId: String,
    val game: String,
    val mode: String,
    val difficulty: String,
    val prompt: String,
    val status: String,
    val you: String,
    val opponent: String,
    val winner: String? = null,
    val solution: String = "",
    val grid: List<String> = emptyList(),
    val seconds: Int = 45,
    val choices: List<String> = emptyList(),
    val puzzleId: String = "",
    val answered: Boolean = false,
)
