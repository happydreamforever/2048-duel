package com.duel2048.app.cube

import com.duel2048.app.cube.magic.domain.CubeSettings
import com.duel2048.app.cube.magic.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FixedSettingsRepository(settings: CubeSettings) : SettingsRepository {
    private val flow = MutableStateFlow(settings)
    override val settingsFlow: StateFlow<CubeSettings> = flow
    override suspend fun getCurrent(): CubeSettings = flow.value
    override suspend fun save(settings: CubeSettings) {
        flow.value = settings
    }
}
