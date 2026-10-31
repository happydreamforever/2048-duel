package com.duel2048.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.fx.GameFx
import com.duel2048.app.fx.Haptics
import com.duel2048.app.fx.LocalGameFx
import com.duel2048.app.fx.SoundFx
import com.duel2048.app.ui.components.AmbientBackground
import com.duel2048.app.ui.landscape.*
import com.duel2048.app.ui.rememberLocalizedContext
import com.duel2048.app.ui.screens.AppChrome
import com.duel2048.app.ui.screens.CubeDuelScreen
import com.duel2048.app.ui.screens.DuelScreen
import com.duel2048.app.ui.screens.SavedGamesScreen
import com.duel2048.app.ui.screens.SoloScreen
import com.duel2048.app.ui.theme.DuelTheme
import com.duel2048.app.ui.theme.LocalLowEffects
import com.duel2048.app.ui.theme.Palettes

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Duel2048App(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val palette = Palettes.byId(settings.themeId)
    val baseContext = LocalContext.current

    val fx = remember {
        GameFx(
            haptics = Haptics(baseContext) { vm.settings.value.haptics },
            sound = SoundFx(baseContext) { vm.settings.value.sound },
        )
    }
    DisposableEffect(Unit) { onDispose { fx.sound.release() } }

    val localized = rememberLocalizedContext(settings.language)

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
    ) {
        DuelTheme(palette) {
            CompositionLocalProvider(
                LocalGameFx provides fx, LocalLowEffects provides settings.lowEffects) {
                    Box(Modifier.fillMaxSize()) {
                        AmbientBackground(palette, animated = !settings.lowEffects)
                        AppChrome(vm, screen) {
                            AnimatedContent(
                                targetState = screen,
                                transitionSpec = {
                                    ContentTransform(
                                        fadeIn(
                                            androidx.compose.animation.core.tween(
                                                if (settings.lowEffects) 0 else 200)) +
                                            scaleIn(
                                                initialScale =
                                                    if (settings.lowEffects) 1f else 0.98f),
                                        fadeOut(
                                            androidx.compose.animation.core.tween(
                                                if (settings.lowEffects) 0 else 140)))
                                },
                                label = "screen",
                                modifier = Modifier.fillMaxSize(),
                            ) { target ->
                                when (target) {
                                    Screen.Home -> LandscapeDashboard(vm)
                                    Screen.SavedGames -> SavedGamesScreen(vm)
                                    Screen.Community ->
                                        LandscapeUtilities(vm, "players", vm::goHome)
                                    Screen.Profile -> LandscapeUtilities(vm, "profile", vm::goHome)
                                    Screen.Login -> {
                                        LandscapeDashboard(vm)
                                        LandscapeAuth(vm)
                                    }
                                    Screen.Matchmaking -> LandscapeQueue(vm, false)
                                    Screen.Duel -> DuelScreen(vm)
                                    Screen.Result -> LandscapeResult(vm, false)
                                    Screen.Solo -> SoloScreen(vm)
                                    Screen.CubeMatchmaking -> LandscapeQueue(vm, true)
                                    Screen.CubeDuel -> CubeDuelScreen(vm)
                                    Screen.CubeResult -> LandscapeResult(vm, true)
                                    Screen.Leaderboard ->
                                        LandscapeUtilities(vm, "leaderboard", vm::goHome)
                                    is Screen.GameModes -> LandscapeDashboard(vm)
                                    is Screen.Challenges -> LandscapeInvitations(vm, target)
                                    Screen.MiniPlay -> LandscapeMini(vm)
                                    Screen.Watch -> LandscapeWatch(vm)
                                }
                            }
                        }
                    }
                }
        }
    }
}
