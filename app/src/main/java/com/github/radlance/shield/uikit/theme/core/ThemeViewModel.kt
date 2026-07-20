package com.github.radlance.shield.uikit.theme.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.radlance.shield.uikit.theme.domain.ThemeRepository
import com.github.radlance.shield.uikit.theme.ui.AppFont
import com.github.radlance.shield.uikit.theme.ui.ColorTheme
import com.github.radlance.shield.uikit.theme.ui.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class ThemeViewModel(
    private val repository: ThemeRepository
): ViewModel() {
    val themeConfiguration: StateFlow<ThemeConfiguration> = repository.themeConfiguration
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000.milliseconds),
            initialValue = ThemeConfiguration(
                themeMode = ThemeMode.LIGHT,
                colorTheme = ColorTheme.DYNAMIC,
                useDynamicColors = true,
                isAmoledMode = false,
                appFont = AppFont.Google
            )
        )

    val currentFont: StateFlow<AppFont> = themeConfiguration
        .map { it.appFont }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000.milliseconds),
            initialValue = AppFont.Google
        )

    fun updateTheme(config: ThemeConfiguration) {
        viewModelScope.launch {
            repository.setThemeConfig(config)
        }
    }

    fun setFont(font: AppFont) {
        val current = themeConfiguration.value
        val newConfig = current.copy(appFont = font)
        updateTheme(newConfig)
    }
}