package com.github.radlance.shield.home.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.github.radlance.shield.R
import com.github.radlance.shield.home.presentation.ServerItem
import com.github.radlance.shield.subscription.domain.SubscriptionAccessStatus
import com.github.radlance.shield.subscription.domain.SubscriptionMetadata
import com.github.radlance.shield.uikit.tokens.icons
import com.github.radlance.shield.uikit.tokens.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleServerList(
    title: String,
    items: List<ServerItem>,
    modifier: Modifier = Modifier,
    metadata: SubscriptionMetadata = SubscriptionMetadata(),
    accessStatus: SubscriptionAccessStatus = SubscriptionAccessStatus.AVAILABLE,
    isInitiallyExpanded: Boolean = true,
    selectedId: String? = null,
    onServerSelected: (String) -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onPing: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    isPinging: Boolean = false,
    error: String? = null
) {
    var isExpanded by remember { mutableStateOf(isInitiallyExpanded) }
    val summary = subscriptionSummary(metadata)
    val hasMetadata = metadata.hasVisibleData()

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ChevronRotation"
    )
    val titleAutoSize = remember {
        TextAutoSize.StepBased(
            minFontSize = 12.sp,
            maxFontSize = 16.sp,
            stepSize = 1.sp
        )
    }

    val handleRefresh: () -> Unit = {
        if (!isRefreshing) onRefresh?.invoke()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.m, vertical = MaterialTheme.spacing.xs)
    ) {
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacing.m,
                        vertical = MaterialTheme.spacing.s
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = titleAutoSize
                    )

                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (accessStatus) {
                                SubscriptionAccessStatus.AVAILABLE -> {
                                    if (metadata.needsAttention()) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                }
                                SubscriptionAccessStatus.EXPIRED,
                                SubscriptionAccessStatus.TRAFFIC_EXHAUSTED ->
                                    MaterialTheme.colorScheme.error
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = MaterialTheme.icons.medium,
                            minHeight = MaterialTheme.icons.medium
                        )
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        )
                        .padding(horizontal = MaterialTheme.spacing.s)
                ) {
                    Text(
                        text = items.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)
                ) {
                    onPing?.let {
                        IconButton(
                            onClick = it,
                            enabled = items.isNotEmpty() && !isPinging,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(MaterialTheme.icons.large)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = stringResource(R.string.ping_servers),
                                modifier = Modifier.size(MaterialTheme.icons.mediumSmall)
                            )
                        }
                    }

                    onRefresh?.let {
                        IconButton(
                            onClick = handleRefresh,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(MaterialTheme.icons.large)
                        ) {
                            AnimatedContent(
                                targetState = isRefreshing,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(200)) + scaleIn(
                                        initialScale = 0.8f,
                                        animationSpec = tween(200)
                                    )) togetherWith
                                            (fadeOut(animationSpec = tween(200)) + scaleOut(
                                                targetScale = 0.8f,
                                                animationSpec = tween(200)
                                            ))
                                },
                                label = "RefreshLoadingTransition"
                            ) { refreshing ->
                                if (refreshing) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(MaterialTheme.icons.mediumSmall)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh",
                                        modifier = Modifier.size(MaterialTheme.icons.mediumSmall),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    onDelete?.let {
                        IconButton(
                            onClick = it,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(MaterialTheme.icons.large)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.delete_subscription),
                                modifier = Modifier.size(MaterialTheme.icons.mediumSmall),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(MaterialTheme.icons.large)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier
                                .size(MaterialTheme.icons.medium)
                                .graphicsLayer { rotationZ = rotationAngle },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.m,
                    vertical = MaterialTheme.spacing.xs
                )
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                ),
                expandFrom = Alignment.Top
            ) + fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                ),
                shrinkTowards = Alignment.Top
            ) + fadeOut(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)
            ) {
                if (hasMetadata) {
                    SubscriptionMetadataContent(
                        metadata = metadata,
                        accessStatus = accessStatus
                    )
                }

                items.forEachIndexed { index, item ->
                    ServerListItem(
                        item = item,
                        isSelected = item.id == selectedId,
                        index = index,
                        totalItems = items.size,
                        onSelect = { onServerSelected(item.id) },
                        enabled = accessStatus == SubscriptionAccessStatus.AVAILABLE
                    )
                }
            }
        }
    }
}
