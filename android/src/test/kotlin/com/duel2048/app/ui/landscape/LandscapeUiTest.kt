package com.duel2048.app.ui.landscape

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.duel2048.app.*
import com.duel2048.shared.social.Puzzles
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h440dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LandscapeUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var vm: MainViewModel

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication() as Application
        app.getSharedPreferences("duel2048", 0)
            .edit()
            .clear()
            .putBoolean("lowfx", true)
            .putBoolean("sound", false)
            .putString("lang", "en")
            .putString("server", "ws://127.0.0.1:1/ws")
            .commit()
        vm = MainViewModel(app)
        compose.setContent { Duel2048App(vm) }
    }

    @After
    fun cleanup() {
        ViewModelStore().apply {
            put("main", vm)
            clear()
        }
    }

    private fun preview(name: String) {
        val dir = System.getenv("DUEL_UI_PREVIEW_DIR") ?: return
        if (name == "login")
            return // Dialog window is validated by semantics; capture the activity surfaces below.
        val folder = java.io.File(dir)
        require(folder.canonicalPath.startsWith("/tmp/"))
        folder.mkdirs()
        compose.waitForIdle()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File(folder, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun guest() {
        compose.onNodeWithText("Practice as Guest").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(Screen.Home, vm.screen.value)
    }

    @Test
    fun launchPopupAndDashboardUtilitiesHaveDirectReturn() {
        preview("login")
        guest()
        preview("dashboard")
        compose.onNodeWithTag("play:MATH").assertIsDisplayed()
        compose.onNodeWithTag("play:WORD_PUZZLE").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Sound effects").assertIsDisplayed()
        preview("settings")
        compose.onNodeWithText("Dashboard", substring = true).performClick()
        compose.onNodeWithTag("play:MATH").assertIsDisplayed()
        assertEquals(Screen.Home, vm.screen.value)
    }

    @Test
    fun dashboardStartsOfflineMathAndSubmitsThroughExistingController() {
        guest()
        compose.onNodeWithTag("mode:TRAIN").performClick()
        compose.onNodeWithTag("play:MATH").performClick()
        compose.waitForIdle()
        assertEquals(Screen.MiniPlay, vm.screen.value)
        val state = checkNotNull(vm.mini.value)
        val answer = Puzzles.mathEntries("easy").first { it.id == state.puzzleId }.answer
        compose.onNodeWithText(state.prompt).assertIsDisplayed()
        preview("math-practice")
        compose.onNodeWithText(answer).performClick()
        compose.onNodeWithText("Submit Answer").performClick()
        compose.waitForIdle()
        assertEquals("over", vm.mini.value?.status)
        assertTrue(vm.mini.value?.won == true)
        compose.onAllNodesWithText("Dashboard").onFirst().performClick()
        compose.onNodeWithTag("play:MATH").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w740dp-h360dp-land-mdpi")
    fun smallLandscapeKeepsGameActionsVisible() {
        guest()
        compose.onNodeWithTag("play:WORD_PUZZLE").assertIsDisplayed()
        compose.onNodeWithTag("play:TILES").assertIsDisplayed()
        preview("dashboard-small")
    }

    @Test
    fun wordGridKeepsNativeOrthogonalOnlySelection() {
        guest()
        compose.onNodeWithTag("mode:TRAIN").performClick()
        compose.onNodeWithTag("play:WORD_PUZZLE").performClick()
        compose.waitForIdle()
        val grid = checkNotNull(vm.mini.value).grid
        preview("word-grid")
        compose.onNodeWithContentDescription("${grid[0][0]}, row 1, column 1").performClick()
        compose.onNodeWithContentDescription("${grid[1][1]}, row 2, column 2").assertIsNotEnabled()
        compose
            .onNodeWithContentDescription("${grid[0][1]}, row 1, column 2")
            .assertIsEnabled()
            .performClick()
        assertEquals(listOf(0, 1), vm.mini.value?.selectedLetters)
        compose.onNodeWithText("Clear").performClick()
        assertEquals(emptyList<Int>(), vm.mini.value?.selectedLetters)
    }
}
