package com.duel2048.app.cube.magic.presentation.cube.engine

interface ICubeRotationEngine {
    val angleX: Float
    val angleY: Float
    val isInertiaActive: Boolean
    
    fun updateRotation(screenDx: Float, screenDy: Float, scale: Float)
    fun startInertia()
    fun stopInertia()
    fun tickInertia()
    fun getRotationState(): CubeRotationState
}
