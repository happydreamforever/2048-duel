package com.duel2048.shared.cube

/** Standard cube faces in the layout used by [CubeState]. */
enum class CubeFace(val letter: Char) {
    U('U'),
    R('R'),
    F('F'),
    D('D'),
    L('L'),
    B('B'),
    ;

    val baseIndex: Int = ordinal * 9
}
