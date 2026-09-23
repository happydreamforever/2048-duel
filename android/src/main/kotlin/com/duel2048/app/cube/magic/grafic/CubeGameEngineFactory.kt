package com.duel2048.app.cube.magic.grafic

/**
 * Factory for creating [ICubeGameEngine] instances.
 * Using a functional interface enables clean Koin injection and easy test doubles:
 *
 * ```kotlin
 * // Production (Koin)
 * single<CubeGameEngineFactory> { CubeGameEngineFactory { shuffleCount -> CubeGameEngine(shuffleCount) } }
 *
 * // Test
 * val factory = CubeGameEngineFactory { _ -> FakeCubeGameEngine() }
 * ```
 */
fun interface CubeGameEngineFactory {
    fun create(shuffleCount: Int): ICubeGameEngine
}