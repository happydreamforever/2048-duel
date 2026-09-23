package com.duel2048.app.cube.magic.presentation.cube.engine

import com.duel2048.app.cube.magic.domain.model.Vector3

class CubePositionResolver {
    private val dist = 2.12f

    fun getTranslation(x: Int, y: Int, z: Int): Vector3 {
        // x, y, z are indices 0, 1, 2
        // We want to map them to -dist, 0, dist
        return Vector3(
            (x - 1) * dist,
            (y - 1) * dist,
            (z - 1) * dist
        )
    }
}
