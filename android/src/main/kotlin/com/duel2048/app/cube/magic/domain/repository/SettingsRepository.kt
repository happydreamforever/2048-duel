package com.duel2048.app.cube.magic.domain.repository

import com.duel2048.app.cube.magic.domain.CubeSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settingsFlow: StateFlow<CubeSettings>
    suspend fun getCurrent(): CubeSettings
    suspend fun save(settings: CubeSettings)
}
