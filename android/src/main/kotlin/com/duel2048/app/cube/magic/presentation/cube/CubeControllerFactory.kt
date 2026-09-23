package com.duel2048.app.cube.magic.presentation.cube

import com.duel2048.app.cube.magic.grafic.ICubeGameEngine

fun interface CubeControllerFactory {
    fun create(engine: ICubeGameEngine): ICubeInteractor
}
