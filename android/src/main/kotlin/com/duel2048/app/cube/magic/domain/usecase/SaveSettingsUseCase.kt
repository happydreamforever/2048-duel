package com.duel2048.app.cube.magic.domain.usecase

import com.duel2048.app.cube.magic.domain.CubeSettings
import com.duel2048.app.cube.magic.domain.repository.SettingsRepository

class SaveSettingsUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(settings: CubeSettings) = repository.save(settings)
}
