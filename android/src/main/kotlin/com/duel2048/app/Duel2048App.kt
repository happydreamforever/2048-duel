package com.duel2048.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import com.duel2048.app.ui.rememberLocalizedContext
import com.duel2048.app.ui.screens.DuelScreen
import com.duel2048.app.ui.screens.HomeScreen
import com.duel2048.app.ui.screens.LoginScreen
import com.duel2048.app.ui.screens.MatchmakingScreen
import com.duel2048.app.ui.screens.ResultScreen
import com.duel2048.app.ui.screens.SoloScreen
import com.duel2048.app.ui.theme.DuelTheme
import com.duel2048.app.ui.theme.LocalLowEffects
import com.duel2048.app.ui.theme.Palettes

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
            CompositionLocalProvider(LocalGameFx provides fx, LocalLowEffects provides settings.lowEffects) {
                Box(Modifier.fillMaxSize()) {
                    AmbientBackground(palette, animated = !settings.lowEffects)
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut()) },
                        label = "screen",
                    ) { target ->
                        when (target) {
                            Screen.Home -> HomeScreen(vm)
                            Screen.Login -> LoginScreen(vm)
                            Screen.Matchmaking -> MatchmakingScreen(vm)
                            Screen.Duel -> DuelScreen(vm)
                            Screen.Result -> ResultScreen(vm)
                            Screen.Solo -> SoloScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
