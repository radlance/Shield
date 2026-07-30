package com.github.radlance.shield.home.presentation

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.radlance.shield.uikit.tokens.components
import com.github.radlance.shield.uikit.tokens.corners
import com.github.radlance.shield.uikit.tokens.icons
import com.github.radlance.shield.uikit.vector.ModeOffOnIcon
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ShieldControlBar(
    isWorking: Boolean,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    connectedAtElapsedRealtime: Long? = null
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
            isWorking -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            isWorking -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimary
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
        enabled = enabled,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = containerColor,
            checkedContainerColor = containerColor,
            contentColor = contentColor,
            checkedContentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
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
                ElapsedTimerText(
                    connectedAtElapsedRealtime = connectedAtElapsedRealtime,
                    contentColor = contentColor
                )
            }
        }
    }
}

@Composable
private fun ElapsedTimerText(
    connectedAtElapsedRealtime: Long?,
    contentColor: Color
) {
    var elapsedSeconds by remember(connectedAtElapsedRealtime) {
        mutableLongStateOf(0L)
    }
    val autoSize = remember {
        TextAutoSize.StepBased(
            minFontSize = 14.sp,
            maxFontSize = 24.sp,
            stepSize = 1.sp
        )
    }

    LaunchedEffect(connectedAtElapsedRealtime) {
        val connectedAt = connectedAtElapsedRealtime ?: return@LaunchedEffect
        while (true) {
            val elapsedMillis = (SystemClock.elapsedRealtime() - connectedAt).coerceAtLeast(0L)
            elapsedSeconds = elapsedMillis / 1_000
            delay((1_000 - elapsedMillis % 1_000).milliseconds)
        }
    }

    val hours = elapsedSeconds / 3_600
    val minutes = elapsedSeconds % 3_600 / 60
    val seconds = elapsedSeconds % 60
    BasicText(
        text = "%02d:%02d:%02d".format(hours, minutes, seconds),
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            color = contentColor,
            fontFeatureSettings = "tnum",
            textAlign = TextAlign.Center
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = autoSize,
        modifier = Modifier.width(128.dp)
    )
}
