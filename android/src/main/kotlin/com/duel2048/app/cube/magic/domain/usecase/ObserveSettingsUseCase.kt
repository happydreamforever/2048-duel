package com.duel2048.app.cube.magic.domain.usecase

import com.duel2048.app.cube.magic.domain.CubeSettings
import com.duel2048.app.cube.magic.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<CubeSettings> = repository.settingsFlow
}
