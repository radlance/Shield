/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.radlance.shield.home.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonScope
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.hypot

/**
 * Material3's toggle FAB with its hardcoded shadow removed.
 *
 * Adapted from Material3 1.5.0-alpha25. The public component has no elevation parameter, so this
 * keeps its layout, motion, shape, colors, ripple, and semantics while omitting shadow elevation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShadowlessToggleFloatingActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ToggleFloatingActionButtonScope.() -> Unit
) {
    val containerColor = ToggleFloatingActionButtonDefaults.containerColor()
    val containerSize = ToggleFloatingActionButtonDefaults.containerSize()
    val containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadius()
    val checkedProgress = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "ToggleFloatingActionButtonProgress"
    )
    val progress = { checkedProgress.value }
    val initialSize = remember(containerSize) { containerSize(0f) }

    Box(Modifier.size(initialSize), contentAlignment = Alignment.TopEnd) {
        val density = LocalDensity.current
        val rippleRadius = remember(initialSize) {
            with(density) {
                val halfSize = initialSize.toPx() / 2
                hypot(halfSize, halfSize).toDp()
            }
        }
        val shape = remember(density, progress, containerCornerRadius) {
            GenericShape { size, _ ->
                val radius = with(density) { containerCornerRadius(progress()).toPx() }
                addRoundRect(RoundRect(size.toRect(), CornerRadius(radius)))
            }
        }

        Box(
            modifier
                .graphicsLayer {
                    shadowElevation = 0f
                    this.shape = shape
                    clip = true
                }
                .drawBehind {
                    val radius = with(density) { containerCornerRadius(progress()).toPx() }
                    drawRoundRect(
                        color = containerColor(progress()),
                        cornerRadius = CornerRadius(radius)
                    )
                }
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    interactionSource = null,
                    indication = ripple(radius = rippleRadius, focusRingShape = shape)
                )
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val sizePx = containerSize(progress()).roundToPx()
                    layout(sizePx, sizePx) {
                        placeable.place(
                            (sizePx - placeable.width) / 2,
                            (sizePx - placeable.height) / 2
                        )
                    }
                }
        ) {
            val scope = remember(progress) {
                object : ToggleFloatingActionButtonScope {
                    override val checkedProgress: Float
                        get() = progress()
                }
            }
            content(scope)
        }
    }
}
