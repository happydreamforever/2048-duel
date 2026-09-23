package com.duel2048.shared.cube

import kotlinx.serialization.Serializable

/** WCA face-turn notation for a 3x3 cube (outer slices only). */
@Serializable
enum class CubeMove {
    U, Ui, U2,
    R, Ri, R2,
    F, Fi, F2,
    D, Di, D2,
    L, Li, L2,
    B, Bi, B2,
    ;

    fun notation(): String = when (this) {
        U -> "U"
        Ui -> "U'"
        U2 -> "U2"
        R -> "R"
        Ri -> "R'"
        R2 -> "R2"
        F -> "F"
        Fi -> "F'"
        F2 -> "F2"
        D -> "D"
        Di -> "D'"
        D2 -> "D2"
        L -> "L"
        Li -> "L'"
        L2 -> "L2"
        B -> "B"
        Bi -> "B'"
        B2 -> "B2"
    }

    fun inverse(): CubeMove = when (this) {
        U -> Ui
        Ui -> U
        U2 -> U2
        R -> Ri
        Ri -> R
        R2 -> R2
        F -> Fi
        Fi -> F
        F2 -> F2
        D -> Di
        Di -> D
        D2 -> D2
        L -> Li
        Li -> L
        L2 -> L2
        B -> Bi
        Bi -> B
        B2 -> B2
    }

    fun quarterTurns(): Int = when (this) {
        U, R, F, D, L, B -> 1
        Ui, Ri, Fi, Di, Li, Bi -> 3
        U2, R2, F2, D2, L2, B2 -> 2
    }

    fun face(): CubeFace = when (this) {
        U, Ui, U2 -> CubeFace.U
        R, Ri, R2 -> CubeFace.R
        F, Fi, F2 -> CubeFace.F
        D, Di, D2 -> CubeFace.D
        L, Li, L2 -> CubeFace.L
        B, Bi, B2 -> CubeFace.B
    }

    companion object {
        private val byNotation = entries.associateBy { it.notation() }

        fun parse(text: String): CubeMove? = byNotation[text.trim()]

        fun parseList(text: String): List<CubeMove> =
            text.split(Regex("\\s+")).mapNotNull { parse(it) }

        /** Base quarter-turn moves used when generating scrambles. */
        val QUARTER_TURNS: List<CubeMove> = listOf(U, R, F, D, L, B)
    }
}
