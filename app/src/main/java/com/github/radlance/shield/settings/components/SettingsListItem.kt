package com.github.radlance.shield.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.radlance.shield.uikit.tokens.corners
import com.github.radlance.shield.uikit.tokens.icons
import com.github.radlance.shield.uikit.tokens.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsListItem(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    index: Int,
    totalCount: Int,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    val shapes = MaterialTheme.shapes
    SegmentedListItem(
        modifier = Modifier,
        selected = false,
        enabled = true,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(
            index,
            totalCount,
            ListItemDefaults.shapes(
                shape = if (totalCount == 1) shapes.large else shapes.extraSmall,
                selectedShape = shapes.extraLargeIncreased,
                pressedShape = shapes.extraLargeIncreased,
                focusedShape = shapes.large,
                hoveredShape = shapes.extraLarge,
                draggedShape = shapes.extraLargeIncreased
            )
        ),
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = MaterialTheme.spacing.s)) }
        },
        leadingContent = leadingIcon?.let {
            { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(MaterialTheme.icons.medium)) }
        },
        trailingContent = if (showChevron) {
            { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(MaterialTheme.icons.medium).clip(RoundedCornerShape(MaterialTheme.corners.small))) }
        } else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = MaterialTheme.spacing.s, top = MaterialTheme.spacing.xs, bottom = MaterialTheme.spacing.xs)) }
}
