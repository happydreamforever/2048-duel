package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.protocol.MatchMode

@Composable
fun CubeMatchmakingScreen(vm: MainViewModel) {
    val cube by vm.cubeMatch.state.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    BackHandler { vm.cancelCubeSearch() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = palette.accent)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.cube_matchmaking),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
        )
        if (cube.mode == MatchMode.PVP && cube.botFallbackMs > 0) {
            Text(
                stringResource(R.string.bot_fallback_in, (cube.botFallbackMs / 1000).coerceAtLeast(1)),
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = { vm.cancelCubeSearch() }) {
            Text(stringResource(R.string.cancel), color = palette.danger)
        }
    }
}
