package com.github.radlance.shield.settings.appearance.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.settings.components.SettingsSectionHeader
import com.github.radlance.shield.settings.components.settingsItemShape
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.theme.ui.ThemeMode
import com.github.radlance.shield.uikit.tokens.spacing

@Composable
fun ThemeModeSelector(themeViewModel: ThemeViewModel) {
    val state by themeViewModel.uiState.collectAsStateWithLifecycle()
    val modes = ThemeMode.entries
    Column {
        SettingsSectionHeader(Icons.Rounded.SettingsBrightness, stringResource(R.string.theme_mode))
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
            modes.forEachIndexed { index, mode ->
                val (icon, title, subtitle) = when (mode) {
                    ThemeMode.LIGHT -> Triple(Icons.Rounded.LightMode, R.string.light, R.string.always_use_light_appearance)
                    ThemeMode.DARK -> Triple(Icons.Rounded.DarkMode, R.string.dark, R.string.always_use_dark_appearance)
                    ThemeMode.SYSTEM -> Triple(Icons.Rounded.AutoMode, R.string.system, R.string.match_system_appearance)
                }
                ListItem(
                    modifier = Modifier.clip(settingsItemShape(index, modes.size)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { themeViewModel.updateTheme(state.configuration.copy(themeMode = mode)) },
                    supportingContent = { Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { RadioButton(selected = state.configuration.themeMode == mode, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                ) { Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs)) }
            }
        }
    }
}
