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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Box(modifier = Modifier.fillMaxSize()) {
            val iconOffsetX by animateDpAsState(
                targetValue = if (isWorking) (-64).dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = 0.65f,
                    stiffness = Spring.StiffnessLow
                ),
                label = "iconOffset"
            )

            Icon(
                imageVector = ModeOffOnIcon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(x = iconOffsetX.roundToPx(), y = 0) }
                    .size(MaterialTheme.icons.xxl)
            )

            AnimatedVisibility(
                visible = isWorking,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                        slideInVertically(
                            initialOffsetY = { 40 },
                            animationSpec = spring(
                                dampingRatio = 0.65f,
                                stiffness = Spring.StiffnessLow
                            )
                        ) +
                        scaleIn(
                            initialScale = 0.8f,
                            animationSpec = spring(
                                dampingRatio = 0.65f,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                        slideOutVertically(
                            targetOffsetY = { 40 },
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        ) +
                        scaleOut(
                            targetScale = 0.8f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 32.dp)
            ) {
                BasicText(
                    text = "00:00:00",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp),
                    modifier = Modifier.widthIn(max = 128.dp)
                )
            }
        }
    }
}