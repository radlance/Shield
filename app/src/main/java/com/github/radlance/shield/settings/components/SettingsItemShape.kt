package com.github.radlance.shield.settings.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import com.github.radlance.shield.uikit.tokens.corners

@Composable
fun settingsItemShape(index: Int, count: Int): Shape {
    val large = MaterialTheme.corners.large
    val small = MaterialTheme.corners.small
    return when {
        count == 1 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(large, large, small, small)
        index == count - 1 -> RoundedCornerShape(small, small, large, large)
        else -> RoundedCornerShape(small)
    }
}
