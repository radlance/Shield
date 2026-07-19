package com.github.radlance.shield.uikit.theme.core

import com.github.radlance.shield.uikit.theme.ui.AppFont
import com.github.radlance.shield.uikit.theme.ui.ColorTheme
import com.github.radlance.shield.uikit.theme.ui.ThemeMode

data class ThemeConfiguration(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorTheme: ColorTheme = ColorTheme.SESSIONS,
    val useDynamicColors: Boolean = true,
    val isAmoledMode: Boolean = false,
    val appFont: AppFont = AppFont.Google
)