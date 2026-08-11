package com.github.radlance.shield.home.presentation.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch

object ServerListTransitions {
    private const val SLIDE_OFFSET_Y = 40
    private const val INITIAL_SCALE = 0.8f

    val enterTransition: EnterTransition =
        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                slideInVertically(
                    initialOffsetY = { SLIDE_OFFSET_Y },
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) +
                scaleIn(
                    initialScale = INITIAL_SCALE,
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = Spring.StiffnessLow
                    )
                )

    val exitTransition: ExitTransition =
        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                slideOutVertically(
                    targetOffsetY = { SLIDE_OFFSET_Y },
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) +
                scaleOut(
                    targetScale = INITIAL_SCALE,
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                )

    @Composable
    fun AnimatedItem(content: @Composable () -> Unit) {
        val scope = rememberCoroutineScope()
        val offsetY = remember { Animatable(0f) }
        var previousY by remember { mutableStateOf<Float?>(null) }

        Box(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    val currentY = coordinates.positionInParent().y
                    val oldY = previousY
                    previousY = currentY

                    if (oldY != null && oldY != currentY) {
                        scope.launch {
                            offsetY.stop()
                            offsetY.snapTo(oldY - currentY)
                            offsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                }
                .graphicsLayer { translationY = offsetY.value }
        ) {
            content()
        }
    }
}
