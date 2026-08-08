package com.github.radlance.shield.settings.appearance.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.theme.ui.ThemeMode
import com.github.radlance.shield.uikit.tokens.components
import com.github.radlance.shield.uikit.tokens.corners
import com.github.radlance.shield.uikit.tokens.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AmoledThemeSelector(themeViewModel: ThemeViewModel) {
    val state by themeViewModel.uiState.collectAsStateWithLifecycle()
    val isDark = state.configuration.themeMode == ThemeMode.DARK ||
        (state.configuration.themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaterialTheme.corners.extraLarge),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            if (isDark) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().height(MaterialTheme.components.buttonLarge).alpha(0.15f), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            }
            ListItem(
                modifier = Modifier,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                supportingContent = { Text(if (isDark) stringResource(R.string.amoled_mode_description) else stringResource(R.string.amoled_available_dark), style = MaterialTheme.typography.bodyMedium) },
                leadingContent = { Icon(Icons.Rounded.Contrast, contentDescription = null, tint = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                trailingContent = {
                    Switch(
                        checked = state.configuration.isAmoledMode,
                        enabled = isDark,
                        onCheckedChange = { enabled -> themeViewModel.updateTheme(state.configuration.copy(isAmoledMode = enabled)) },
                        colors = SwitchDefaults.colors(uncheckedThumbColor = MaterialTheme.colorScheme.outline, uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                }
            ) { Text(stringResource(R.string.amoled_mode), style = MaterialTheme.typography.titleMedium) }
        }
    }
}
