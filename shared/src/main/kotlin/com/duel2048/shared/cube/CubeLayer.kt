package com.duel2048.shared.cube

/** A layer in cube space: axis 0=X, 1=Y, 2=Z; coord -1, 0, or 1. */
enum class CubeLayer(val axis: Int, val coord: Int, val quarterDegrees: Int, val face: CubeFace? = null) {
    U(1, 1, -90, CubeFace.U),
    R(0, 1, -90, CubeFace.R),
    F(2, 1, -90, CubeFace.F),
    D(1, -1, 90, CubeFace.D),
    L(0, -1, 90, CubeFace.L),
    B(2, -1, 90, CubeFace.B),
    // M follows L, E follows D, and S follows F.
    M(0, 0, 90),
    E(1, 0, 90),
    S(2, 0, -90),
}
