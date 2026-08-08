package com.github.radlance.shield.settings.appearance.components

import androidx.compose.ui.graphics.Color
import com.github.radlance.shield.uikit.theme.ui.AppColorSchemes
import com.github.radlance.shield.uikit.theme.ui.ColorTheme

data class ThemeOption(
    val colorTheme: ColorTheme,
    val displayName: String,
    val primaryColor: Color,
    val gradientColors: List<Color>
)

fun themeOptions(currentPrimary: Color, currentPrimaryContainer: Color): List<ThemeOption> = listOf(
    ThemeOption(ColorTheme.DYNAMIC, "Dynamic", currentPrimary, listOf(currentPrimary, currentPrimaryContainer)),
    ThemeOption(ColorTheme.SHIELD, "Shield", AppColorSchemes.ShieldLightColorScheme.primary, listOf(AppColorSchemes.ShieldLightColorScheme.primary, AppColorSchemes.ShieldLightColorScheme.primaryContainer)),
    ThemeOption(ColorTheme.LAGOON, "Lagoon", AppColorSchemes.LagoonLightColorScheme.primary, listOf(AppColorSchemes.LagoonLightColorScheme.primary, AppColorSchemes.LagoonLightColorScheme.primaryContainer)),
    ThemeOption(ColorTheme.HARVEST, "Harvest", AppColorSchemes.HarvestLightColorScheme.primary, listOf(AppColorSchemes.HarvestLightColorScheme.primary, AppColorSchemes.HarvestLightColorScheme.primaryContainer)),
    ThemeOption(ColorTheme.GROVE, "Grove", AppColorSchemes.GroveLightColorScheme.primary, listOf(AppColorSchemes.GroveLightColorScheme.primary, AppColorSchemes.GroveLightColorScheme.primaryContainer)),
    ThemeOption(ColorTheme.SAKURA, "Sakura", AppColorSchemes.SakuraLightColorScheme.primary, listOf(AppColorSchemes.SakuraLightColorScheme.primary, AppColorSchemes.SakuraLightColorScheme.primaryContainer)),
    ThemeOption(ColorTheme.ALPINE, "Alpine", AppColorSchemes.AlpineLightColorScheme.primary, listOf(AppColorSchemes.AlpineLightColorScheme.primary, AppColorSchemes.AlpineLightColorScheme.primaryContainer)),
    ThemeOption(ColorTheme.TWILIGHT, "Twilight", AppColorSchemes.TwilightLightColorScheme.primary, listOf(AppColorSchemes.TwilightLightColorScheme.primary, AppColorSchemes.TwilightLightColorScheme.primaryContainer))
)
