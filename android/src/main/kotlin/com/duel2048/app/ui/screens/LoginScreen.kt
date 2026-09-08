package com.duel2048.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.R
import com.duel2048.app.ui.components.GlassCard
import com.duel2048.app.ui.components.NeonButton
import com.duel2048.app.ui.errorText
import com.duel2048.app.ui.theme.LocalPalette

@Composable
fun LoginScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val login by vm.login.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    var name by remember { mutableStateOf(settings.accountName.ifBlank { settings.playerName }) }
    var password by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    BackHandler { vm.cancelLogin() }

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        IconButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = palette.textSecondary)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium, color = palette.accent)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                val colors = duelFieldColors()
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                login.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(errorText(it), color = palette.danger, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
                NeonButton(
                    stringResource(if (login.busy) R.string.connecting_short else R.string.log_in),
                    enabled = !login.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.submitAuth(name, password, register = false) }
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    stringResource(R.string.create_account),
                    enabled = !login.busy,
                    colors = listOf(palette.accent, palette.orbColors[2]),
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.submitAuth(name, password, register = true) }
            }
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = { vm.cancelLogin() }) {
                Text(stringResource(R.string.play_offline), color = palette.textSecondary, fontWeight = FontWeight.Bold)
            }
            Text(settings.serverUrl, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }

    if (showSettings) {
        SettingsSheet(settings, onDismiss = { showSettings = false }) { vm.updateSettings(it) }
    }
}
