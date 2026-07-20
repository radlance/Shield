package com.github.radlance.shield.uikit.theme.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.github.radlance.shield.uikit.theme.core.ThemeConfiguration

@Composable
fun getColorScheme(
    themeConfiguration: ThemeConfiguration,
    isSystemInDarkTheme: Boolean = isSystemInDarkTheme()
): ColorScheme {

    val isDarkMode = when (themeConfiguration.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme
    }

    var baseScheme = when (themeConfiguration.colorTheme) {
        ColorTheme.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (isDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDarkMode) AppColorSchemes.ShieldDarkColorScheme else AppColorSchemes.ShieldLightColorScheme
            }
        }

        ColorTheme.TWILIGHT ->
            if (isDarkMode) AppColorSchemes.TwilightDarkColorScheme else AppColorSchemes.TwilightLightColorScheme

        ColorTheme.SAKURA ->
            if (isDarkMode) AppColorSchemes.SakuraDarkColorScheme else AppColorSchemes.SakuraLightColorScheme

        ColorTheme.SHIELD ->
            if (isDarkMode) AppColorSchemes.ShieldDarkColorScheme else AppColorSchemes.ShieldLightColorScheme

        ColorTheme.HARVEST ->
            if (isDarkMode) AppColorSchemes.HarvestDarkColorScheme else AppColorSchemes.HarvestLightColorScheme

        ColorTheme.GROVE ->
            if (isDarkMode) AppColorSchemes.GroveDarkColorScheme else AppColorSchemes.GroveLightColorScheme

        ColorTheme.ALPINE ->
            if (isDarkMode) AppColorSchemes.AlpineDarkColorScheme else AppColorSchemes.AlpineLightColorScheme

        ColorTheme.LAGOON ->
            if (isDarkMode) AppColorSchemes.LagoonDarkColorScheme else AppColorSchemes.LagoonLightColorScheme
    }

    if (isDarkMode && themeConfiguration.colorTheme != ColorTheme.DYNAMIC) {
        baseScheme = baseScheme.deepenSurfaces(fraction = 0.12f)
    }

    return if (isDarkMode && themeConfiguration.isAmoledMode) {
        baseScheme.toAmoled()
    } else {
        baseScheme
    }
}

fun ColorScheme.deepenSurfaces(fraction: Float): ColorScheme {
    return copy(
        background = lerp(background, Color.Black, fraction),
        surface = lerp(surface, Color.Black, fraction),
        surfaceDim = lerp(surfaceDim, Color.Black, fraction),
        surfaceBright = lerp(surfaceBright, Color.Black, fraction),
        surfaceContainerLowest = lerp(surfaceContainerLowest, Color.Black, fraction),
        surfaceContainerLow = lerp(surfaceContainerLow, Color.Black, fraction),
        surfaceContainer = lerp(surfaceContainer, Color.Black, fraction),
        surfaceContainerHigh = lerp(surfaceContainerHigh, Color.Black, fraction),
        surfaceContainerHighest = lerp(surfaceContainerHighest, Color.Black, fraction),
        surfaceVariant = lerp(surfaceVariant, Color.Black, fraction * 0.5f),
        onBackground = lerp(onBackground, Color.Black, fraction * 0.15f),
        onSurface = lerp(onSurface, Color.Black, fraction * 0.15f),
        onSurfaceVariant = lerp(onSurfaceVariant, Color.Black, fraction * 0.15f),
    )
}

fun ColorScheme.toAmoled(): ColorScheme {
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF101010),
        scrim = Color.Black
    )
}