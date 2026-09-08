package com.duel2048.shared.bot

import com.duel2048.shared.engine.Board
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.engine.Rules
import kotlin.math.abs

/**
 * Heuristic 2048 player used as the practice opponent and as matchmaking fallback.
 * Two-ply greedy search over a classic evaluation (empty cells, smoothness,
 * monotonicity, corner anchoring, garbage penalty).
 */
object Bot {

    data class Profile(val name: String, val intervalMs: Long, val jitterMs: Long, val depth: Int)

    val DEFAULT = Profile(name = "Nova", intervalMs = 750, jitterMs = 350, depth = 2)

    fun chooseMove(state: GameState, depth: Int = 2): Direction? {
        var best: Direction? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (d in Direction.entries) {
            val r = GameEngine.move(state, d)
            if (!r.events.moved) continue
            var score = evaluate(r.state.board) + r.events.scoreGained * 0.05
            if (depth > 1) {
                var childBest = Double.NEGATIVE_INFINITY
                for (d2 in Direction.entries) {
                    val r2 = GameEngine.move(r.state, d2)
                    if (!r2.events.moved) continue
                    val s2 = evaluate(r2.state.board) + r2.events.scoreGained * 0.05
                    if (s2 > childBest) childBest = s2
                }
                if (childBest != Double.NEGATIVE_INFINITY) score = score * 0.4 + childBest * 0.6 else score -= 50.0
            }
            if (score > bestScore) {
                bestScore = score
                best = d
            }
        }
        return best
    }

    fun evaluate(board: Board): Double {
        val n = board.size
        var empty = 0
        var smooth = 0.0
        var garbage = 0
        var maxValue = 0
        var maxCell = -1 to -1
        val lv = Array(n) { IntArray(n) }
        for (r in 0 until n) for (c in 0 until n) {
            val t = board[r, c]
            if (t == null) { empty++; continue }
            if (t.isGarbage) { garbage++; continue }
            lv[r][c] = Rules.log2(t.value)
            if (t.value > maxValue) { maxValue = t.value; maxCell = r to c }
        }
        for (r in 0 until n) for (c in 0 until n) {
            if (lv[r][c] == 0) continue
            if (c + 1 < n && lv[r][c + 1] != 0) smooth -= abs(lv[r][c] - lv[r][c + 1])
            if (r + 1 < n && lv[r + 1][c] != 0) smooth -= abs(lv[r][c] - lv[r + 1][c])
        }
        var mono = 0.0
        for (r in 0 until n) {
            var inc = 0.0; var dec = 0.0
            for (c in 0 until n - 1) { val d = (lv[r][c + 1] - lv[r][c]).toDouble(); if (d > 0) inc += d else dec -= d }
            mono += maxOf(inc, dec) - minOf(inc, dec)
        }
        for (c in 0 until n) {
            var inc = 0.0; var dec = 0.0
            for (r in 0 until n - 1) { val d = (lv[r + 1][c] - lv[r][c]).toDouble(); if (d > 0) inc += d else dec -= d }
            mono += maxOf(inc, dec) - minOf(inc, dec)
        }
        val corner = if (maxCell.first in listOf(0, n - 1) && maxCell.second in listOf(0, n - 1)) Rules.log2(maxValue) * 1.5 else 0.0
        return empty * 3.0 + smooth * 0.6 + mono * 0.8 + corner - garbage * 3.0
    }
}
