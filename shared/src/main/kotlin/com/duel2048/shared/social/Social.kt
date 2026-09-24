package com.duel2048.shared.social

import kotlinx.serialization.Serializable
import java.math.BigInteger
import kotlin.random.Random

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

val CHAT_PHRASES = listOf(
    "Good luck", "Nice", "Wow", "Hurry up", "GG", "Thanks", "Oops", "Close one",
    "🔥", "😂", "👍", "👏", "😎", "🤝", "💀", "✨",
)

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
data class MiniAnswerBody(val roomId: String, val name: String, val answer: String)

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
)

object Puzzles {
    private val words4 = listOf("STAR", "MILE", "WAVE", "BOLT", "ECHO", "LAMP", "FROG", "MIND", "GOLD", "SHIP")
    private val words6 = listOf("PLANET", "BRIDGE", "SILVER", "ORBIT", "PUZZLE", "ROCKET", "GARDEN", "CASTLE", "MARKET", "BUTTON")

    fun math(difficulty: String, random: Random = Random.Default): Pair<String, String> {
        val (digits, ops) = when (difficulty) {
            "medium" -> 2 to listOf('+', '-', '*', '/')
            "hard" -> 2 to listOf('+', '-', '*', '/')
            "very_hard" -> random.nextInt(3, 25) to listOf('+', '-', '*')
            else -> 1 to listOf('+', '-', '*', '/')
        }
        val a = number(digits, random)
        val b = number(if (difficulty == "hard") 2 else digits.coerceAtMost(if (digits > 6) 6 else digits), random)
        val op = ops.random(random)
        val (left, right, text) = if (op == '/' ) {
            val divisor = b.coerceAtLeast(BigInteger.ONE)
            val product = a.multiply(divisor)
            Triple(product, divisor, "$product / $divisor")
        } else {
            Triple(a, b, "$a $op $b")
        }
        val answer = when (op) {
            '+' -> left.add(right)
            '-' -> left.subtract(right)
            '*' -> left.multiply(right)
            else -> left.divide(right)
        }
        return text to answer.toString()
    }

    fun word(size: Int, random: Random = Random.Default): Triple<List<String>, String, Int> {
        val word = if (size >= 6) words6.random(random) else words4.random(random)
        val n = if (size >= 6) 6 else 4
        val row = random.nextInt(n)
        val col = random.nextInt((n - word.length + 1).coerceAtLeast(1))
        val grid = MutableList(n) { _ ->
            CharArray(n) { random.nextInt(26).let { 'A' + it } }.concatToString()
        }.toMutableList()
        val chars = grid[row].toCharArray()
        word.forEachIndexed { i, ch -> if (col + i < n) chars[col + i] = ch }
        grid[row] = chars.concatToString()
        val seconds = if (n == 6) 70 else 40
        return Triple(grid, word, seconds)
    }

    private fun number(digits: Int, random: Random): BigInteger {
        val d = digits.coerceIn(1, 24)
        val sb = StringBuilder()
        sb.append(random.nextInt(1, 10))
        repeat(d - 1) { sb.append(random.nextInt(0, 10)) }
        return BigInteger(sb.toString())
    }
}
