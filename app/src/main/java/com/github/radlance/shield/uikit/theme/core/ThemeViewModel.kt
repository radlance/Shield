package com.github.radlance.shield.uikit.theme.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.radlance.shield.uikit.theme.domain.ThemeRepository
import com.github.radlance.shield.uikit.theme.ui.AppFont
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class ThemeUiState(
    val configuration: ThemeConfiguration = ThemeConfiguration(),
    val isInitialized: Boolean = false
)

class ThemeViewModel(
    private val repository: ThemeRepository
) : ViewModel() {
    val uiState: StateFlow<ThemeUiState> = repository.themeConfiguration
        .map { configuration ->
            ThemeUiState(
                configuration = configuration,
                isInitialized = true
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000.milliseconds),
            initialValue = ThemeUiState()
        )

    val currentFont: StateFlow<AppFont> = uiState
        .map { it.configuration.appFont }
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
        val current = uiState.value.configuration
        val newConfig = current.copy(appFont = font)
        updateTheme(newConfig)
    }
}
