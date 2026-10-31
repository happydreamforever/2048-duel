@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.duel2048.app.ui.landscape

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duel2048.app.MainViewModel
import com.duel2048.app.ui.errorText
import com.duel2048.app.ui.landscape.LandscapeText as Text
import com.duel2048.app.ui.theme.LocalPalette

@Composable
fun LandscapeAuth(vm: MainViewModel) {
    val state by vm.login.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable {
        mutableStateOf(settings.accountName.ifBlank { settings.playerName })
    }
    // Passwords intentionally never enter saved-instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!state.busy) vm.cancelLogin() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !state.busy,
                dismissOnClickOutside = false)) {
            NeonPane(
                Modifier.widthIn(max = 700.dp)
                    .fillMaxWidth(.92f)
                    .fillMaxHeight(.94f)
                    .imePadding()) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                Modifier.weight(.32f).fillMaxHeight(),
                                contentAlignment = Alignment.Center) {
                                    Art(modifier = Modifier.fillMaxSize())
                                }
                            Column(
                                Modifier.weight(.68f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        if (register) "Join 2048 Duel" else "Welcome to 2048 Duel",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.Bold)
                                    Row {
                                        FilterChip(
                                            !register,
                                            {
                                                register = false
                                                mismatch = false
                                            },
                                            label = { Text("Sign In") })
                                        Spacer(Modifier.width(8.dp))
                                        FilterChip(
                                            register,
                                            {
                                                register = true
                                                mismatch = false
                                            },
                                            label = { Text("Create Account") })
                                    }
                                    if (state.error != null)
                                        Text(
                                            errorText(checkNotNull(state.error)),
                                            color = p.danger,
                                            style = MaterialTheme.typography.bodySmall)
                                    if (mismatch) Text("Passwords must match.", color = p.danger)
                                    OutlinedTextField(
                                        name,
                                        { name = it.take(16) },
                                        label = { Text("Username") },
                                        singleLine = true,
                                        enabled = !state.busy,
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions =
                                            KeyboardOptions(
                                                autoCorrect = false, imeAction = ImeAction.Next))
                                    OutlinedTextField(
                                        password,
                                        { password = it.take(128) },
                                        label = { Text("Password") },
                                        singleLine = true,
                                        enabled = !state.busy,
                                        modifier = Modifier.fillMaxWidth(),
                                        visualTransformation =
                                            if (visible) VisualTransformation.None
                                            else PasswordVisualTransformation(),
                                        keyboardOptions =
                                            KeyboardOptions(keyboardType = KeyboardType.Password),
                                        trailingIcon = {
                                            TextButton(onClick = { visible = !visible }) {
                                                Text(
                                                    if (visible) "Hide" else "Show",
                                                    style = MaterialTheme.typography.labelSmall)
                                            }
                                        })
                                    if (register)
                                        OutlinedTextField(
                                            confirm,
                                            { confirm = it.take(128) },
                                            label = { Text("Confirm password") },
                                            singleLine = true,
                                            enabled = !state.busy,
                                            modifier = Modifier.fillMaxWidth(),
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions =
                                                KeyboardOptions(
                                                    keyboardType = KeyboardType.Password))
                                    ArcadeAction(
                                        if (state.busy) "Connecting…"
                                        else if (register) "Create Account" else "Sign In",
                                        Modifier.fillMaxWidth(),
                                        enabled =
                                            !state.busy &&
                                                name.isNotBlank() &&
                                                password.isNotEmpty()) {
                                            focus.clearFocus()
                                            mismatch = register && password != confirm
                                            if (!mismatch) vm.submitAuth(name, password, register)
                                        }
                                    TextButton(
                                        onClick = {
                                            focus.clearFocus()
                                            vm.cancelLogin()
                                        },
                                        enabled = !state.busy) {
                                            Text("Practice as Guest")
                                        }
                                }
                        }
                }
        }
}
