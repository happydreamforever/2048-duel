package com.duel2048.app.cube.model3d

import kotlin.math.tan

/**
 * Orbit camera around the cube (the cube itself never rotates, so the scene lights stay put
 * relative to it and each face keeps its look while orbiting). Produces the matrices handed
 * to the renderer AND the exact same math for touch: picking rays and screen projection.
 */
class CubeCamera(val fovYDegrees: Float = 34f) {

    var azimuth = 32f
        private set
    var elevation = 24f
        private set

    private var width = 1
    private var height = 1

    val aspect: Float get() = if (height > 0) width.toFloat() / height else 1f
    val hasViewport: Boolean get() = width > 1 && height > 1

    /** Distance that fills the limiting axis (width in portrait, height in landscape). */
    val distance: Float
        get() {
            val halfTan = tan(Math.toRadians(fovYDegrees / 2.0)).toFloat()
            return maxOf(SILHOUETTE / halfTan, SILHOUETTE / (halfTan * aspect)) * 0.99f
        }

    fun setViewport(w: Int, h: Int) {
        width = w
        height = h
    }

    fun orbitBy(dAzimuth: Float, dElevation: Float) {
        azimuth += dAzimuth
        elevation = (elevation + dElevation).coerceIn(-80f, 80f)
    }

    /** World -> eye: back off along -Z after orbiting the cube. */
    fun viewMatrix(): FloatArray {
        val orbit = CubeMath.multiply(CubeMath.rotationAxis(1f, 0f, 0f, elevation), CubeMath.rotationAxis(0f, 1f, 0f, azimuth))
        return CubeMath.multiply(CubeMath.translation(0f, 0f, -distance), orbit)
    }

    /** Camera's world transform (inverse of [viewMatrix]), as renderers expect it. */
    fun modelMatrix(): FloatArray = checkNotNull(CubeMath.inverse(viewMatrix()))

    fun projectionMatrix(): FloatArray = CubeMath.perspective(fovYDegrees, aspect, NEAR, FAR)

    /** Cube-space ray through view pixel (x, y): (origin, unit direction); null before layout. */
    fun pickRay(x: Float, y: Float): Pair<FloatArray, FloatArray>? {
        if (!hasViewport) return null
        val inv = CubeMath.inverse(CubeMath.multiply(projectionMatrix(), viewMatrix())) ?: return null
        val ndcX = 2f * x / width - 1f
        val ndcY = 1f - 2f * y / height
        val p0 = CubeMath.transformPoint(inv, ndcX, ndcY, -1f)
        val p1 = CubeMath.transformPoint(inv, ndcX, ndcY, 1f)
        return Pair(p0, CubeMath.normalize(floatArrayOf(p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2])))
    }

    /** Cube-space point -> view pixels; null before layout. */
    fun projectToScreen(p: FloatArray): FloatArray? {
        if (!hasViewport) return null
        val ndc = CubeMath.transformPoint(CubeMath.multiply(projectionMatrix(), viewMatrix()), p[0], p[1], p[2])
        return floatArrayOf((ndc[0] + 1f) * 0.5f * width, (1f - ndc[1]) * 0.5f * height)
    }

    companion object {
        const val NEAR = 0.1f
        const val FAR = 50f
        /** Worst-case silhouette half-extent of the 3x3 cube (outer faces at +-1.5). */
        private const val SILHOUETTE = 2.05f
    }
}
