package com.duel2048.app.cube.magic.presentation.cube.engine

interface ICubeProjectionCalculator {
    val projectionMatrix: FloatArray
    fun onSurfaceChanged(width: Int, height: Int)
}
