package com.duel2048.app.ui.landscape

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.duel2048.app.cube.CubeDuelUiState
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.ui.screens.CubeDuelOverlay
import com.duel2048.app.ui.theme.DuelTheme
import com.duel2048.app.ui.theme.Palettes
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h440dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CubeControlUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sliceAndFaceButtonsUseExistingNotationsAndInverse() {
        val turns = mutableListOf<String>()
        compose.setContent {
            DuelTheme(Palettes.NEON) {
                CubeDuelOverlay(
                    CubeDuelUiState(phase = DuelPhase.PLAYING),
                    onLeave = {},
                    onGiveUp = {},
                    onTurn = { turns.add(it.notation()) },
                    opponentView = {},
                    playerView = {})
            }
        }
        for (face in listOf("U", "D", "L", "R", "F", "B", "M", "E", "S")) compose
            .onNodeWithText(face)
            .performClick()
        assertEquals(listOf("U", "D", "L", "R", "F", "B", "M", "E", "S"), turns)
        compose.onNodeWithText("Inverse  ′").performClick()
        compose.onNodeWithText("M′").performClick()
        assertEquals("M'", turns.last())
    }

    @Test
    fun reconnectingDisablesTurnsAndCameraResetDoesNotSubmitMove() {
        val turns = mutableListOf<String>()
        var resets = 0
        compose.setContent {
            DuelTheme(Palettes.NEON) {
                CubeDuelOverlay(
                    CubeDuelUiState(phase = DuelPhase.PLAYING, reconnecting = true),
                    onLeave = {},
                    onGiveUp = {},
                    onTurn = { turns.add(it.notation()) },
                    onResetView = { resets++ },
                    opponentView = {},
                    playerView = {})
            }
        }
        compose.onNodeWithText("M").assertIsNotEnabled()
        compose.onNodeWithText("Reset View").performScrollTo().performClick()
        assertEquals(1, resets)
        assertTrue(turns.isEmpty())
    }
}
