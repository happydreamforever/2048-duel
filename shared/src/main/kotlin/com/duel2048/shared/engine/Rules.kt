package com.duel2048.shared.engine

import kotlin.math.max

/** Tunable match rules. Shared so client prediction and server agree exactly. */
object Rules {
    const val BOARD_SIZE = 4
    const val INITIAL_TILES = 2

    /** Energy needed to launch one garbage tile at the opponent. */
    const val ATTACK_COST = 12

    /** Hard cap on garbage sent by a single move (prevents one-shot kills). */
    const val MAX_GARBAGE_PER_MOVE = 3

    /** Merges next to a garbage tile needed to shatter it. */
    const val GARBAGE_HP = 2

    const val MATCH_DURATION_MS = 150_000L
    const val COUNTDOWN_SECONDS = 3

    /** 2 -> 1, 4 -> 2, ... (exact for powers of two, which is all we ever store). */
    fun log2(value: Int): Int = if (value <= 0) 0 else 31 - Integer.numberOfLeadingZeros(value)

    /** Energy for one merge producing [value]. Small merges give 1; 64 gives 3; 2048 gives 8. */
    fun energyForMerge(value: Int): Int = max(1, log2(value) - 3)

    /** Extra energy when several merges happen in one swipe. */
    fun comboBonus(merges: Int): Int = if (merges >= 2) 2 * (merges - 1) else 0

    /** Chance (out of 10) that a spawned tile is a 4 instead of a 2. */
    const val FOUR_CHANCE_IN_TEN = 1
}
