package com.duel2048.app.cube

import android.os.SystemClock
import com.duel2048.app.cube.magic.domain.CubeSettings
import com.duel2048.app.cube.magic.domain.TimeProvider
import com.duel2048.app.cube.magic.domain.cube.CubeFaceGeometryResolver
import com.duel2048.app.cube.magic.domain.cube.CubeInteractionProcessor
import com.duel2048.app.cube.magic.domain.cube.CubeRotationMath
import com.duel2048.app.cube.magic.domain.cube.CubeSliceInteractionResolver
import com.duel2048.app.cube.magic.domain.cube.CubeVisibleFacesCalculator
import com.duel2048.app.cube.magic.domain.cube.GestureClassifier
import com.duel2048.app.cube.magic.domain.math.MatrixMath
import com.duel2048.app.cube.magic.grafic.CubeGameEngine
import com.duel2048.app.cube.magic.grafic.CubeGameEngineFactory
import com.duel2048.app.cube.magic.grafic.MatrixTracker
import com.duel2048.app.cube.magic.grafic.PickingService
import com.duel2048.app.cube.magic.presentation.cube.AndroidCubeLogger
import com.duel2048.app.cube.magic.presentation.cube.CubeControllerFactory
import com.duel2048.app.cube.magic.presentation.cube.CubeGameInteractor
import com.duel2048.app.cube.magic.presentation.cube.CubeRenderEngine
import com.duel2048.app.cube.magic.presentation.cube.CubeViewModel
import com.duel2048.app.cube.magic.presentation.cube.engine.CubeDrawCommandFactory
import com.duel2048.app.cube.magic.presentation.cube.engine.CubeProjectionCalculator
import com.duel2048.app.cube.magic.presentation.cube.engine.CubeRotationEngine
import com.duel2048.app.cube.magic.presentation.cube.engine.CubeSliceResolver
import com.duel2048.app.cube.magic.presentation.cube.engine.CubeTraversalEngine
import com.duel2048.app.cube.magic.domain.usecase.ObserveSettingsUseCase

/**
 * Manual wiring for the ported MagicCube-Android stack (MIT, Gustavo Brilhante).
 * See android/src/main/kotlin/com/duel2048/app/cube/magic/ATTRIBUTION.md
 */
object CubeDependencies {

    private val pvpSettings = CubeSettings(shuffle = 0, speed = 5, size = 9)

    fun createCubeViewModel(): CubeViewModel {
        val matrixMath = MatrixMath()
        val engineFactory = CubeGameEngineFactory { shuffle -> CubeGameEngine(shuffle, matrixMath) }
        val rotationEngine = CubeRotationEngine()
        val projection = CubeProjectionCalculator(matrixMath)
        val sliceResolver = CubeSliceResolver()
        val drawFactory = CubeDrawCommandFactory(matrixMath)
        val traversal = CubeTraversalEngine(MatrixTracker(matrixMath), sliceResolver, drawFactory)
        val renderEngine = CubeRenderEngine(rotationEngine, projection, traversal)
        val controllerFactory = CubeControllerFactory { engine ->
            CubeGameInteractor(
                engine = engine,
                interactionProcessor = CubeInteractionProcessor(
                    GestureClassifier(),
                    CubeRotationMath(),
                    CubeFaceGeometryResolver(),
                    CubeSliceInteractionResolver(CubeFaceGeometryResolver(), CubeRotationMath()),
                    CubeVisibleFacesCalculator(),
                    matrixMath,
                ),
                pickingService = PickingService(matrixMath),
                timeProvider = TimeProvider { SystemClock.elapsedRealtime() },
                logger = AndroidCubeLogger(),
            )
        }
        val observeSettings = ObserveSettingsUseCase(FixedSettingsRepository(pvpSettings))
        return CubeViewModel(observeSettings, engineFactory, controllerFactory, renderEngine)
    }
}
