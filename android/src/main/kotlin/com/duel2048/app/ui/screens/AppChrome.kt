@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.duel2048.app.ui.screens

import androidx.compose.runtime.Composable
import com.duel2048.app.MainViewModel
import com.duel2048.app.Screen
import com.duel2048.app.ui.landscape.LandscapeShell

@Composable
internal fun AppChrome(vm: MainViewModel, screen: Screen, content: @Composable () -> Unit) =
    LandscapeShell(vm, screen, content)
