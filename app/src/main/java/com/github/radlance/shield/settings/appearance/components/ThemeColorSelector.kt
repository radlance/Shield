package com.github.radlance.shield.settings.appearance.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.settings.components.SettingsSectionHeader
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.tokens.spacing

@Composable
fun ThemeColorSelector(themeViewModel: ThemeViewModel) {
    val state by themeViewModel.uiState.collectAsStateWithLifecycle()
    val currentPrimary = MaterialTheme.colorScheme.primary
    val currentContainer = MaterialTheme.colorScheme.primaryContainer
    val options = remember(currentPrimary, currentContainer) { themeOptions(currentPrimary, currentContainer) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionHeader(Icons.Rounded.Palette, stringResource(R.string.color_theme))
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            options.chunked(2).forEach { rowOptions ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    rowOptions.forEach { option ->
                        Box(modifier = Modifier.weight(1f)) {
                            ThemeCard(option, state.configuration.colorTheme == option.colorTheme) {
                                themeViewModel.updateTheme(state.configuration.copy(colorTheme = option.colorTheme))
                            }
                        }
                    }
                    if (rowOptions.size == 1) Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
