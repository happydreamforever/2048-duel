package com.duel2048.app.cube.model3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal column-major 4x4 matrix and 3D vector helpers for the glb cube renderer
 * (pure Kotlin, unit-tested in CubeModelMathTest; no android or OpenGL imports).
 */
object CubeMath {

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** out = a * b (column-major, OpenGL convention). */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
                out[col * 4 + row] = sum
            }
        }
        return out
    }

    fun rotationAxis(axisX: Float, axisY: Float, axisZ: Float, degrees: Float): FloatArray {
        val len = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
        val x = axisX / len
        val y = axisY / len
        val z = axisZ / len
        val rad = Math.toRadians(degrees.toDouble())
        // Quarter and half turns must stay exact: cos(90°) and sin(180°) round to ~1e-16,
        // snap both to 0 so committed turns never leave residue in the poses.
        val c = cos(rad).toFloat().let { if (abs(it) < 1e-6f) 0f else it }
        val s = sin(rad).toFloat().let { if (abs(it) < 1e-6f) 0f else it }
        val t = 1f - c
        return floatArrayOf(
            t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0f,
            t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0f,
            t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    fun translation(x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        x, y, z, 1f,
    )

    /** OpenGL perspective matrix (column-major) with vertical fov in degrees. */
    fun perspective(fovYDegrees: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / Math.tan(Math.toRadians(fovYDegrees.toDouble() / 2.0)).toFloat()
        val nf = 1f / (near - far)
        return floatArrayOf(
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (far + near) * nf, -1f,
            0f, 0f, 2f * far * near * nf, 0f,
        )
    }

    /** General 4x4 inverse (column-major); returns null for singular matrices. */
    fun inverse(m: FloatArray): FloatArray? {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (abs(det) < 1e-12f) return null
        det = 1f / det
        for (i in 0 until 16) inv[i] *= det
        return inv
    }

    /** v as (x,y,z,w=1); result divided by w. */
    fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float): FloatArray {
        val w = m[3] * x + m[7] * y + m[11] * z + m[15]
        return floatArrayOf(
            (m[0] * x + m[4] * y + m[8] * z + m[12]) / w,
            (m[1] * x + m[5] * y + m[9] * z + m[13]) / w,
            (m[2] * x + m[6] * y + m[10] * z + m[14]) / w,
        )
    }

    /** v as a direction (w=0), no division. */
    fun transformDir(m: FloatArray, x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
        m[0] * x + m[4] * y + m[8] * z,
        m[1] * x + m[5] * y + m[9] * z,
        m[2] * x + m[6] * y + m[10] * z,
    )

    fun cross(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    fun dot(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    fun normalize(v: FloatArray): FloatArray {
        val len = sqrt(dot(v, v))
        if (len < 1e-12f) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
    }

    /**
     * Slab ray/AABB intersection. Ray origin o, normalized direction d, box [min, max].
     * Returns the entry distance along the ray, or null when missed / box behind the ray.
     */
    fun rayAabb(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, min: FloatArray, max: FloatArray): Float? {
        var tmin = Float.NEGATIVE_INFINITY
        var tmax = Float.POSITIVE_INFINITY
        val o = floatArrayOf(ox, oy, oz)
        val d = floatArrayOf(dx, dy, dz)
        for (i in 0 until 3) {
            if (abs(d[i]) < 1e-9f) {
                if (o[i] < min[i] || o[i] > max[i]) return null
            } else {
                val inv = 1f / d[i]
                var t1 = (min[i] - o[i]) * inv
                var t2 = (max[i] - o[i]) * inv
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                if (t1 > tmin) tmin = t1
                if (t2 < tmax) tmax = t2
                if (tmin > tmax) return null
            }
        }
        return if (tmax < 0f) null else tmin.coerceAtLeast(0f)
    }
}
