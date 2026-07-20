package com.github.radlance.shield.uikit.theme.data

import com.github.radlance.shield.uikit.theme.core.ThemeConfiguration
import com.github.radlance.shield.uikit.theme.domain.ThemeRepository

class LocalThemeRepository(
    private val dataStore: ThemeDataStore
) : ThemeRepository {

    override val themeConfiguration = dataStore.themeConfiguration

    override suspend fun setThemeConfig(config: ThemeConfiguration) {
        dataStore.updateThemeConfig(config)
    }

    override suspend fun resetThemeDefaults() {
        dataStore.resetToDefaults()
    }
}