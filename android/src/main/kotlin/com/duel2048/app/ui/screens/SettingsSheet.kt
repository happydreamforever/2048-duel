package com.duel2048.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duel2048.app.R
import com.duel2048.app.data.CubeRenderer
import com.duel2048.app.data.UserSettings
import com.duel2048.app.ui.Languages
import com.duel2048.app.ui.theme.LocalPalette

@Composable
internal fun duelFieldColors(): TextFieldColors {
    val palette = LocalPalette.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.surfaceBorder,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.textSecondary,
        cursorColor = palette.accent,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(settings: UserSettings, onDismiss: () -> Unit, onChange: ((UserSettings) -> UserSettings) -> Unit) {
    val palette = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fieldColors = duelFieldColors()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.bgTop) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
            OutlinedTextField(
                value = settings.playerName,
                onValueChange = { v -> onChange { it.copy(playerName = v.take(16)) } },
                label = { Text(stringResource(R.string.player_name)) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.serverUrl,
                onValueChange = { v -> onChange { it.copy(serverUrl = v) } },
                label = { Text(stringResource(R.string.server_url)) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.server_hint), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)

            Text(stringResource(R.string.language), color = palette.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Languages.all.forEach { (tag, label) ->
                    FilterChip(
                        selected = settings.language == tag,
                        onClick = { onChange { it.copy(language = tag) } },
                        label = { Text(if (tag.isBlank()) stringResource(R.string.language_system) else label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accent.copy(alpha = 0.25f),
                            selectedLabelColor = palette.textPrimary,
                            labelColor = palette.textSecondary,
                        ),
                    )
                }
            }

            Text(stringResource(R.string.cube_renderer), color = palette.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.cubeRenderer == CubeRenderer.CUBE2.id,
                    onClick = { onChange { it.copy(cubeRenderer = CubeRenderer.CUBE2.id) } },
                    label = { Text(stringResource(R.string.cube_renderer_cube2)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent.copy(alpha = 0.25f),
                        selectedLabelColor = palette.textPrimary,
                        labelColor = palette.textSecondary,
                    ),
                )
                FilterChip(
                    selected = settings.cubeRenderer == CubeRenderer.MAGIC.id,
                    onClick = { onChange { it.copy(cubeRenderer = CubeRenderer.MAGIC.id) } },
                    label = { Text(stringResource(R.string.cube_renderer_magic)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent.copy(alpha = 0.25f),
                        selectedLabelColor = palette.textPrimary,
                        labelColor = palette.textSecondary,
                    ),
                )
            }

            ToggleRow(stringResource(R.string.haptics), settings.haptics) { v -> onChange { it.copy(haptics = v) } }
            ToggleRow(stringResource(R.string.sound_effects), settings.sound) { v -> onChange { it.copy(sound = v) } }
            ToggleRow(stringResource(R.string.reduce_effects), settings.lowEffects) { v -> onChange { it.copy(lowEffects = v) } }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = palette.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = Color.White),
        )
    }
}
