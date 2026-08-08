package com.github.radlance.shield.settings.appearance.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.github.radlance.shield.R
import com.github.radlance.shield.uikit.tokens.icons
import com.github.radlance.shield.uikit.tokens.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeCard(option: ThemeOption, selected: Boolean, onClick: () -> Unit) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier.fillMaxWidth(),
        shapes = ToggleButtonShapes(
            shape = ToggleButtonDefaults.squareShape,
            pressedShape = ToggleButtonDefaults.pressedShape,
            checkedShape = ToggleButtonDefaults.roundShape
        ),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = MaterialTheme.spacing.s)) {
            Box(modifier = Modifier.size(MaterialTheme.icons.large).clip(CircleShape).background(Brush.linearGradient(option.gradientColors)), contentAlignment = Alignment.Center) {
                if (selected) {
                    Surface(modifier = Modifier.size(MaterialTheme.icons.mediumSmall), shape = CircleShape, color = MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.selected_theme), tint = option.primaryColor, modifier = Modifier.padding(MaterialTheme.spacing.xxs))
                    }
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            Text(option.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}
