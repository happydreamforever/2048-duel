package com.duel2048.app.cube.magic.domain.cube

import com.duel2048.app.cube.magic.domain.model.FaceTangents
import com.duel2048.app.cube.magic.domain.model.Vector3
import kotlin.math.abs

interface FaceGeometryResolver {
    fun faceLocalTangents(normal: Vector3): FaceTangents
}

class CubeFaceGeometryResolver : FaceGeometryResolver {
    override fun faceLocalTangents(normal: Vector3): FaceTangents = when {
        abs(normal.x) > 0.5f -> FaceTangents(Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f))
        abs(normal.y) > 0.5f -> FaceTangents(Vector3(1f, 0f, 0f), Vector3(0f, 0f, 1f))
        else -> FaceTangents(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f))
    }
}
