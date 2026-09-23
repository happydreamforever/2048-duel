package com.duel2048.app.cube.magic.presentation.cube.engine

import com.duel2048.app.cube.magic.domain.CubeSettings
import com.duel2048.app.cube.magic.grafic.ICubeGameEngine
import com.duel2048.app.cube.magic.grafic.IMatrixTracker
import com.duel2048.app.cube.magic.presentation.cube.CubeDrawCommand

class CubeTraversalEngine(
    private val matrixTracker: IMatrixTracker,
    private val sliceResolver: CubeSliceResolver,
    private val commandFactory: CubeDrawCommandFactory
) : ICubeTraversalEngine {

    private val dist = 2.12f

    override fun buildFrame(
        engine: ICubeGameEngine,
        settings: CubeSettings,
        rotationState: CubeRotationState,
        projectionMatrix: FloatArray
    ): List<CubeDrawCommand> {
        matrixTracker.reset()

        // 1. Initial camera/position setup
        matrixTracker.translate(0f, 0f, (-20 + settings.size).toFloat())

        // 2. Global cube rotation (view angles)
        matrixTracker.rotate(rotationState.angleX, 0f, 1f, 0f)
        matrixTracker.rotate(rotationState.angleY, 1f, 0f, 0f)

        engine.prepareFrameRotation()

        val rotState = engine.rotation
        val commands = mutableListOf<CubeDrawCommand>()

        // 3. Iterate through indices to build the grid
        // indexAxisZ (height) -> World Y -> Slice Z
        // indexAxisY (depth) -> World Z -> Slice Y
        // indexAxisX (width) -> World X -> Slice X

        for (z in 0..2) { // z index maps to Y position: 0->1, 1->0, 2->-1
            val worldY = (1 - z) * dist
            for (y in 0..2) { // y index maps to Z position: 0->-1, 1->0, 2->1
                val worldZ = (y - 1) * dist
                for (x in 0..2) { // x index maps to X position: 0->-1, 1->0, 2->1
                    val worldX = (x - 1) * dist

                    matrixTracker.push()

                    // Apply slice rotations at the origin before translation to rotate around the cube's center
                    if (sliceResolver.shouldRotateY(rotState.activeSlice, z)) {
                        matrixTracker.rotate(engine.rotatedAngle, 0f, 1f, 0f)
                    }
                    if (sliceResolver.shouldRotateZ(rotState.activeSlice, y)) {
                        matrixTracker.rotate(engine.rotatedAngle, 0f, 0f, 1f)
                    }
                    if (sliceResolver.shouldRotateX(rotState.activeSlice, x)) {
                        matrixTracker.rotate(engine.rotatedAngle, 1f, 0f, 0f)
                    }

                    matrixTracker.translate(worldX, worldY, worldZ)

                    // Correct indexing for the grid
                    val cubeIndex = engine.cubeGrid[x][z][y]
                    commands.add(commandFactory.createCommand(engine.cubes[cubeIndex], projectionMatrix, matrixTracker))

                    if (rotationState.isInertiaActive) {
                        updateFaceCenterPositions(engine, cubeIndex)
                    }

                    matrixTracker.pop()
                }
            }
        }

        return commands
    }

    private fun updateFaceCenterPositions(engine: ICubeGameEngine, cubeIndex: Int) {
        engine.faceCenterCubes.forEachIndexed { idx, entry ->
            if (cubeIndex == entry.first) {
                engine.faceCenterPositions[idx].z = -matrixTracker.getZ()
                engine.faceCenterPositions[idx].y = matrixTracker.getY()
                engine.faceCenterPositions[idx].x = matrixTracker.getX()
            }
        }
    }
}
