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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.radlance.shield.R
import com.github.radlance.shield.home.presentation.ServerItem
import com.github.radlance.shield.subscription.domain.SubscriptionAccessStatus
import com.github.radlance.shield.subscription.domain.SubscriptionMetadata
import com.github.radlance.shield.uikit.tokens.icons
import com.github.radlance.shield.uikit.tokens.spacing
import com.github.radlance.shield.uikit.vector.KeepOffIcon

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
    isPinned: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onPing: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    isPinging: Boolean = false,
    error: String? = null,
    dragHandleModifier: Modifier? = null,
    isDragging: Boolean = false
) {
    var isExpanded by rememberSaveable { mutableStateOf(isInitiallyExpanded) }
    var showActionsMenu by remember { mutableStateOf(false) }
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
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.01f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SubscriptionDragScale"
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
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(MaterialTheme.icons.large)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.collapse else R.string.expand
                        ),
                        modifier = Modifier
                            .size(MaterialTheme.icons.medium)
                            .graphicsLayer { rotationZ = rotationAngle },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(MaterialTheme.spacing.xs))

                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)
                    ) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = stringResource(R.string.pinned_subscription),
                                modifier = Modifier.size(MaterialTheme.icons.mediumSmall),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        BasicText(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = titleAutoSize,
                            modifier = Modifier.weight(1f)
                        )
                    }

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

                dragHandleModifier?.let { handleModifier ->
                    val dragHandleInteractionSource = remember { MutableInteractionSource() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = handleModifier
                            .size(MaterialTheme.icons.large)
                            .clickable(
                                interactionSource = dragHandleInteractionSource,
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = null,
                            modifier = Modifier.size(MaterialTheme.icons.mediumSmall),
                            tint = if (isDragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                if (onTogglePin != null || onPing != null || onRefresh != null || onDelete != null) {
                    Box {
                        IconButton(
                            onClick = { showActionsMenu = true },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(MaterialTheme.icons.large)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.subscription_actions),
                                modifier = Modifier.size(MaterialTheme.icons.mediumSmall),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false },
                            modifier = Modifier.width(180.dp),
                            shape = RoundedCornerShape(24.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp
                        ) {
                            onTogglePin?.let { togglePin ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (isPinned) R.string.unpin else R.string.pin
                                            )
                                        )
                                    },
                                    onClick = {
                                        showActionsMenu = false
                                        togglePin()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isPinned) {
                                                KeepOffIcon
                                            } else {
                                                Icons.Rounded.PushPin
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(MaterialTheme.icons.mediumSmall),
                                            tint = if (isPinned) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                            }

                            if (onPing != null || onRefresh != null || onDelete != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            onPing?.let { ping ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ping_servers)) },
                                    onClick = {
                                        showActionsMenu = false
                                        ping()
                                    },
                                    enabled = items.isNotEmpty() && !isPinging,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Speed,
                                            contentDescription = null,
                                            modifier = Modifier.size(MaterialTheme.icons.mediumSmall)
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                            }

                            if (onRefresh != null || onDelete != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            onRefresh?.let {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.refresh)) },
                                    onClick = {
                                        showActionsMenu = false
                                        handleRefresh()
                                    },
                                    enabled = !isRefreshing,
                                    leadingIcon = {
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
                                                    contentDescription = null,
                                                    modifier = Modifier.size(MaterialTheme.icons.mediumSmall),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                            }

                            if (onRefresh != null && onDelete != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            onDelete?.let { delete ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = {
                                        showActionsMenu = false
                                        delete()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(MaterialTheme.icons.mediumSmall),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                            }
                        }
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
