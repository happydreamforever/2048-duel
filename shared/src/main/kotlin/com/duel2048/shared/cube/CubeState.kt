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
                intArrayOf(0, 2, 8, 6),
                intArrayOf(1, 5, 7, 3),
                intArrayOf(18, 9, 45, 36),
                intArrayOf(19, 10, 46, 37),
                intArrayOf(20, 11, 47, 38),
            ),
            CubeFace.R to arrayOf(
                intArrayOf(9, 11, 17, 15),
                intArrayOf(10, 14, 16, 12),
                intArrayOf(2, 29, 45, 26),
                intArrayOf(5, 32, 48, 23),
                intArrayOf(8, 35, 51, 20),
            ),
            CubeFace.F to arrayOf(
                intArrayOf(18, 20, 26, 24),
                intArrayOf(19, 23, 25, 21),
                intArrayOf(6, 27, 47, 17),
                intArrayOf(7, 28, 50, 14),
                intArrayOf(8, 29, 51, 11),
            ),
            CubeFace.D to arrayOf(
                intArrayOf(27, 29, 35, 33),
                intArrayOf(28, 32, 34, 30),
                intArrayOf(24, 42, 51, 15),
                intArrayOf(25, 43, 52, 12),
                intArrayOf(26, 44, 53, 9),
            ),
            CubeFace.L to arrayOf(
                intArrayOf(36, 38, 44, 42),
                intArrayOf(37, 41, 43, 39),
                intArrayOf(0, 18, 45, 35),
                intArrayOf(3, 21, 48, 32),
                intArrayOf(6, 24, 51, 29),
            ),
            CubeFace.B to arrayOf(
                intArrayOf(45, 47, 53, 51),
                intArrayOf(46, 50, 52, 48),
                intArrayOf(2, 11, 35, 42),
                intArrayOf(1, 14, 34, 39),
                intArrayOf(0, 17, 33, 36),
            ),
        )
    }
}
