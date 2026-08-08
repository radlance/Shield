package com.github.radlance.shield.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.radlance.shield.uikit.tokens.spacing

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    index: Int,
    totalCount: Int,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val shapes = MaterialTheme.shapes
    androidx.compose.material3.SegmentedListItem(
        modifier = Modifier,
        selected = false,
        enabled = enabled,
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
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedIconColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = MaterialTheme.spacing.s, top = MaterialTheme.spacing.xs, bottom = MaterialTheme.spacing.xs)) }
}
