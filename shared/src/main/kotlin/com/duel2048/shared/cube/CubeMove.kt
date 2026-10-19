package com.duel2048.shared.cube

import kotlinx.serialization.Serializable

/** Standard face and middle-slice notation for a 3x3 cube. */
@Serializable
enum class CubeMove(val layer: CubeLayer, private val turns: Int) {
    U(CubeLayer.U, 1), Ui(CubeLayer.U, 3), U2(CubeLayer.U, 2),
    R(CubeLayer.R, 1), Ri(CubeLayer.R, 3), R2(CubeLayer.R, 2),
    F(CubeLayer.F, 1), Fi(CubeLayer.F, 3), F2(CubeLayer.F, 2),
    D(CubeLayer.D, 1), Di(CubeLayer.D, 3), D2(CubeLayer.D, 2),
    L(CubeLayer.L, 1), Li(CubeLayer.L, 3), L2(CubeLayer.L, 2),
    B(CubeLayer.B, 1), Bi(CubeLayer.B, 3), B2(CubeLayer.B, 2),
    M(CubeLayer.M, 1), Mi(CubeLayer.M, 3), M2(CubeLayer.M, 2),
    E(CubeLayer.E, 1), Ei(CubeLayer.E, 3), E2(CubeLayer.E, 2),
    S(CubeLayer.S, 1), Si(CubeLayer.S, 3), S2(CubeLayer.S, 2),
    ;

    fun notation(): String = layer.name + when (turns) {
        1 -> ""
        2 -> "2"
        else -> "'"
    }

    fun inverse(): CubeMove = of(layer, 4 - turns)

    fun quarterTurns(): Int = turns

    /** Outer face, or null for M/E/S middle slices. */
    fun face(): CubeFace? = layer.face

    /** 180° turn on the same layer as this move. */
    fun doubleTurn(): CubeMove = of(layer, 2)

    companion object {
        private val byNotation = entries.associateBy { it.notation() }
        private val byLayerAndTurns = entries.associateBy { it.layer to it.turns }

        fun of(layer: CubeLayer, quarterTurns: Int): CubeMove =
            byLayerAndTurns.getValue(layer to quarterTurns)

        fun parse(text: String): CubeMove? = byNotation[text.trim()]

        fun parseList(text: String): List<CubeMove> =
            text.split(Regex("\\s+")).mapNotNull { parse(it) }

        /** Outer quarter-turn moves used when generating standard scrambles. */
        val QUARTER_TURNS: List<CubeMove> = listOf(U, R, F, D, L, B)
    }
}
