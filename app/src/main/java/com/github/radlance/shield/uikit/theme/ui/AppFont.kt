package com.github.radlance.shield.uikit.theme.ui

import androidx.annotation.FontRes
import com.github.radlance.shield.R

enum class AppFont(
    val id: String,
    val displayName: String,
    @FontRes val resId: Int
) {
    Google(
        id = "google",
        displayName = "Google Sans",
        resId = R.font.google_sans_flex
    ),
    Outfit(
        id = "outfit",
        displayName = "Outfit",
        resId = R.font.outfit
    ),
    Manrope(
        id = "manrope",
        displayName = "Manrope",
        resId = R.font.manrope
    ),
    Urbanist(
        id = "urbanist",
        displayName = "Urbanist",
        resId = R.font.urbanist
    ),
    Figtree(
        id = "figtree",
        displayName = "Figtree",
        resId = R.font.figtree
    ),
    Garamond(
        id = "garamond",
        displayName = "Garamond",
        resId = R.font.eb_garamond
    )
}