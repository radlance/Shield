package com.github.radlance.shield.navigation.destination

import androidx.annotation.StringRes
import com.github.radlance.shield.R

sealed class BottomDestination(
    @StringRes val titleId: Int
)

data object Home : BottomDestination(titleId = R.string.home)

object Settings : BottomDestination(titleId = R.string.settings)