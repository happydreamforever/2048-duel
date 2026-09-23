package com.duel2048.shared.cube

import com.duel2048.shared.engine.SplitMix64

/** WCA-style scramble generation (outer face turns, no immediate inverse on same face). */
object CubeScramble {

    const val DEFAULT_LENGTH = 20

    fun generate(seed: Long, length: Int = DEFAULT_LENGTH): List<CubeMove> {
        val rng = SplitMix64(seed)
        val moves = ArrayList<CubeMove>(length)
        var lastFace: CubeFace? = null
        repeat(length) {
            val candidates = CubeMove.QUARTER_TURNS.filter { it.face() != lastFace }
            val base = candidates[rng.nextInt(candidates.size)]
            val variant = when (rng.nextInt(3)) {
                0 -> base
                1 -> base.inverse()
                else -> base.doubleTurn()
            }
            moves += variant
            lastFace = variant.face()
        }
        return moves
    }

    fun notations(seed: Long, length: Int = DEFAULT_LENGTH): List<String> =
        generate(seed, length).map { it.notation() }

    fun apply(seed: Long, length: Int = DEFAULT_LENGTH): CubeState =
        CubeState.SOLVED.applyAll(generate(seed, length))

    fun inverse(moves: List<CubeMove>): List<CubeMove> =
        moves.asReversed().map { it.inverse() }
}
