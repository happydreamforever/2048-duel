package com.duel2048.shared.cube

/**
 * 54-sticker cube state (URFDLB, 9 stickers per face, row-major).
 * Deterministic and shared by client + server for cube-solve PvP.
 */
data class CubeState(val stickers: CharArray) {

    init {
        require(stickers.size == 54) { "expected 54 stickers, got ${stickers.size}" }
    }

    fun copyState(): CubeState = CubeState(stickers.copyOf())

    fun isSolved(): Boolean = stickers.contentEquals(SOLVED.stickers)

    /** Compact fingerprint for anti-cheat / sync checks. */
    fun fingerprint(): String = stickers.concatToString()

    fun apply(move: CubeMove): CubeState {
        val out = stickers.copyOf()
        repeat(move.quarterTurns()) {
            for (cycle in MOVE_CYCLES[move.face()]!!) {
                val tmp = out[cycle[0]]
                for (i in 0 until cycle.size - 1) {
                    out[cycle[i]] = out[cycle[i + 1]]
                }
                out[cycle.last()] = tmp
            }
        }
        return CubeState(out)
    }

    fun applyAll(moves: List<CubeMove>): CubeState = moves.fold(this) { s, m -> s.apply(m) }

    companion object {
        private val SOLVED_STRING = buildString {
            repeat(9) { append(CubeFace.U.letter) }
            repeat(9) { append(CubeFace.R.letter) }
            repeat(9) { append(CubeFace.F.letter) }
            repeat(9) { append(CubeFace.D.letter) }
            repeat(9) { append(CubeFace.L.letter) }
            repeat(9) { append(CubeFace.B.letter) }
        }

        val SOLVED: CubeState = CubeState(SOLVED_STRING.toCharArray())

        /** One clockwise quarter turn for each face (WCA orientation: white up, green front). */
        private val MOVE_CYCLES: Map<CubeFace, Array<IntArray>> = mapOf(
            CubeFace.U to arrayOf(
                intArrayOf(0, 6, 8, 2),
                intArrayOf(1, 3, 7, 5),
                intArrayOf(18, 9, 45, 36),
                intArrayOf(19, 10, 46, 37),
                intArrayOf(20, 11, 47, 38),
            ),
            CubeFace.R to arrayOf(
                intArrayOf(9, 15, 17, 11),
                intArrayOf(10, 12, 16, 14),
                intArrayOf(2, 20, 29, 51),
                intArrayOf(5, 23, 32, 48),
                intArrayOf(8, 26, 35, 45),
            ),
            CubeFace.F to arrayOf(
                intArrayOf(18, 24, 26, 20),
                intArrayOf(19, 21, 25, 23),
                intArrayOf(6, 44, 29, 9),
                intArrayOf(7, 41, 28, 12),
                intArrayOf(8, 38, 27, 15),
            ),
            CubeFace.D to arrayOf(
                intArrayOf(27, 33, 35, 29),
                intArrayOf(28, 30, 34, 32),
                intArrayOf(24, 42, 51, 15),
                intArrayOf(25, 43, 52, 16),
                intArrayOf(26, 44, 53, 17),
            ),
            CubeFace.L to arrayOf(
                intArrayOf(36, 42, 44, 38),
                intArrayOf(37, 39, 43, 41),
                intArrayOf(0, 53, 27, 18),
                intArrayOf(3, 50, 30, 21),
                intArrayOf(6, 47, 33, 24),
            ),
            CubeFace.B to arrayOf(
                intArrayOf(45, 51, 53, 47),
                intArrayOf(46, 48, 52, 50),
                intArrayOf(0, 11, 35, 42),
                intArrayOf(1, 14, 34, 39),
                intArrayOf(2, 17, 33, 36),
            ),
        )
    }
}
