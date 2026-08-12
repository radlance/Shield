package com.github.radlance.shield.home.presentation.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

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
}
