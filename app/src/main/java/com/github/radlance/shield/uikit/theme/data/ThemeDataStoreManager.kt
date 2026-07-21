package com.github.radlance.shield.uikit.theme.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.radlance.shield.uikit.theme.core.ThemeConfiguration
import com.github.radlance.shield.uikit.theme.ui.AppFont
import com.github.radlance.shield.uikit.theme.ui.ColorTheme
import com.github.radlance.shield.uikit.theme.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface ThemeDataStore {

    val themeConfiguration: Flow<ThemeConfiguration>

    suspend fun updateThemeConfig(config: ThemeConfiguration)

    suspend fun resetToDefaults()
}

internal class BaseThemeDataStore(
    private val dataStore: DataStore<Preferences>
) : ThemeDataStore {

    override val themeConfiguration: Flow<ThemeConfiguration> = dataStore.data
        .catch {
            emit(emptyPreferences())
        }
        .map { prefs ->
            val savedFontName = prefs[APP_FONT_KEY] ?: AppFont.Google.name
            val appFont = try {
                AppFont.valueOf(savedFontName)
            } catch (_: IllegalArgumentException) {
                AppFont.Google
            }

            ThemeConfiguration(
                themeMode = ThemeMode.valueOf(prefs[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name),
                colorTheme = ColorTheme.valueOf(prefs[COLOR_THEME_KEY] ?: ColorTheme.SHIELD.name),
                useDynamicColors = prefs[USE_DYNAMIC_COLORS_KEY] ?: false,
                isAmoledMode = prefs[IS_AMOLED_MODE_KEY] ?: false,
                appFont = appFont
            )
        }

    override suspend fun updateThemeConfig(config: ThemeConfiguration) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = config.themeMode.name
            prefs[COLOR_THEME_KEY] = config.colorTheme.name
            prefs[USE_DYNAMIC_COLORS_KEY] = config.useDynamicColors
            prefs[IS_AMOLED_MODE_KEY] = config.isAmoledMode
            prefs[APP_FONT_KEY] = config.appFont.name
        }
    }

    override suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
        private val USE_DYNAMIC_COLORS_KEY = booleanPreferencesKey("use_dynamic_colors")
        private val IS_AMOLED_MODE_KEY = booleanPreferencesKey("is_amoled_mode")
        private val APP_FONT_KEY = stringPreferencesKey("app_font")
    }
}