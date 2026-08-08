package com.github.radlance.shield.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.github.radlance.shield.R
import com.github.radlance.shield.localization.AppLanguage
import com.github.radlance.shield.uikit.tokens.spacing

@Composable
fun LanguageSelector(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    SettingsSectionHeader(Icons.Rounded.Language, stringResource(R.string.language))
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)
    ) {
        AppLanguage.entries.forEachIndexed { index, language ->
            ListItem(
                modifier = Modifier
                    .clip(settingsItemShape(index, AppLanguage.entries.size))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onLanguageSelected(language) },
                trailingContent = {
                    RadioButton(
                        selected = selectedLanguage == language,
                        onClick = null
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                Text(
                    text = stringResource(language.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs)
                )
            }
        }
    }
}

private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.RUSSIAN -> R.string.language_russian
    }
