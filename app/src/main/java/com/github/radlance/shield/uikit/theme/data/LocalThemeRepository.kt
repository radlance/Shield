package com.github.radlance.shield.uikit.theme.data

import com.github.radlance.shield.uikit.theme.core.ThemeConfiguration
import com.github.radlance.shield.uikit.theme.domain.ThemeRepository
import kotlinx.coroutines.flow.Flow

class LocalThemeRepository : ThemeRepository {
    override val themeConfiguration: Flow<ThemeConfiguration>
        get() = TODO("Not yet implemented")

    override suspend fun setThemeConfig(config: ThemeConfiguration) {
        TODO("Not yet implemented")
    }

    override suspend fun resetThemeDefaults() {
        TODO("Not yet implemented")
    }
}