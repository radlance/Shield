package com.github.radlance.shield.home.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.radlance.shield.uikit.tokens.components
import com.github.radlance.shield.uikit.tokens.corners
import com.github.radlance.shield.uikit.tokens.icons
import com.github.radlance.shield.uikit.vector.ModeOffOnIcon

@Composable
fun ShieldControlBar(
    isWorking: Boolean,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isWorking) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isWorking) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ContentColor"
    )

    FilledIconToggleButton(
        checked = isWorking,
        onCheckedChange = { onStartStop() },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = containerColor,
            checkedContainerColor = containerColor,
            contentColor = contentColor,
            checkedContentColor = contentColor
        ),
        shapes = IconToggleButtonShapes(
            shape = CircleShape,
            pressedShape = RoundedCornerShape(MaterialTheme.corners.extraLarge),
            checkedShape = RoundedCornerShape(MaterialTheme.corners.medium)
        ),
        modifier = modifier
            .size(
                width = MaterialTheme.components.onboardingImageContainer,
                height = MaterialTheme.components.controlBarItemWidthWide
            )
    ) {
        Icon(
            imageVector = ModeOffOnIcon,
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.icons.xxl)
        )
    }
}