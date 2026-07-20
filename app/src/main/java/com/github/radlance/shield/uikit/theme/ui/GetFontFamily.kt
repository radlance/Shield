package com.github.radlance.shield.uikit.theme.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalTextApi::class)
fun getFontFamily(appFont: AppFont): FontFamily {
    return FontFamily(
        Font(
            resId = appFont.resId,
            variationSettings = FontVariation.Settings(FontVariation.weight(300)),
            weight = FontWeight.Light
        ),
        Font(
            resId = appFont.resId,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
            weight = FontWeight.Normal
        ),
        Font(
            resId = appFont.resId,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
            weight = FontWeight.Medium
        ),
        Font(
            resId = appFont.resId,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
            weight = FontWeight.SemiBold
        ),
        Font(
            resId = appFont.resId,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
            weight = FontWeight.Bold
        )
    )
}