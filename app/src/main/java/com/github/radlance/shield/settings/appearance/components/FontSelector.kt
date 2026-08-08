package com.github.radlance.shield.settings.appearance.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.settings.components.SettingsSectionHeader
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.theme.ui.AppFont
import com.github.radlance.shield.uikit.theme.ui.getFontFamily
import com.github.radlance.shield.uikit.tokens.spacing
import androidx.compose.ui.res.stringResource

@Composable
fun FontSelector(themeViewModel: ThemeViewModel) {
    val currentFont by themeViewModel.currentFont.collectAsStateWithLifecycle()
    SettingsSectionHeader(Icons.Rounded.TextFields, stringResource(R.string.app_font))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.xs)
    ) {
        items(AppFont.entries) { font ->
            FilterChip(
                selected = currentFont == font,
                onClick = { themeViewModel.setFont(font) },
                label = { Text(font.displayName, fontFamily = getFontFamily(font), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge) }
            )
        }
    }
}
