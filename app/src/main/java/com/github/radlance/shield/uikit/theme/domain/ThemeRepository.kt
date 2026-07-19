package com.github.radlance.shield.uikit.theme.domain

import com.github.radlance.shield.uikit.theme.core.ThemeConfiguration
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {

    val themeConfiguration: Flow<ThemeConfiguration>

    suspend fun setThemeConfig(config: ThemeConfiguration)

    suspend fun resetThemeDefaults()
}