package com.github.radlance.shield.home.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.github.radlance.shield.home.presentation.ServerItem
import com.github.radlance.shield.uikit.tokens.icons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerListItem(
    item: ServerItem,
    isSelected: Boolean,
    index: Int,
    totalItems: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SegmentedListItem(
        modifier = modifier,
        selected = isSelected,
        enabled = enabled,
        leadingContent = {
            Text(
                text = item.leadingIcon,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            item.description?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessHigh)
                ),
                exit = scaleOut(
                    animationSpec = spring(stiffness = Spring.StiffnessHigh)
                ) + fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessHigh)
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(MaterialTheme.icons.medium)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(MaterialTheme.icons.small)
                    )
                }
            }
        },
        onClick = {
            if (enabled) onSelect()
        },
        shapes = ListItemDefaults.segmentedShapes(
            index,
            totalItems,
            ListItemDefaults.shapes(
                shape = if (totalItems == 1) shapes.large else shapes.extraSmall,
                selectedShape = shapes.extraLargeIncreased,
                pressedShape = shapes.extraLargeIncreased,
                focusedShape = shapes.large,
                hoveredShape = shapes.extraLarge,
                draggedShape = shapes.extraLargeIncreased
            )
        ),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Text(item.title)
    }
}
