package com.github.radlance.shield.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.radlance.shield.R
import com.github.radlance.shield.common.presentation.InfoLayout
import com.github.radlance.shield.uikit.tokens.components
import com.github.radlance.shield.uikit.tokens.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleServerList(
    title: String,
    items: List<MockServerItem>,
    modifier: Modifier = Modifier,
    isInitiallyExpanded: Boolean = true,
    selectedId: Int? = null,
    onServerSelected: (Int) -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(isInitiallyExpanded) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ChevronRotation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "RefreshInfinite")
    val refreshRotation by if (isRefreshing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing)
            ),
            label = "RefreshRotation"
        )
    } else {
        rememberUpdatedState(0f)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacing.m,
                        vertical = MaterialTheme.spacing.s
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = items.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.spacing.s,
                                vertical = MaterialTheme.spacing.xxs
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)
                ) {
                    onRefresh?.let { refreshAction ->
                        IconButton(
                            onClick = refreshAction,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = refreshRotation },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = rotationAngle },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
                items.forEachIndexed { index, item ->
                    val isSelected = item.id == selectedId

                    SegmentedListItem(
                        selected = isSelected,
                        leadingContent = {
                            Text(
                                text = item.leadingIcon,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            item.description?.let {
                                Text(
                                    it,
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
                                        .size(24.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = { onServerSelected(item.id) },
                        shapes = ListItemDefaults.segmentedShapes(
                            index,
                            items.size,
                            ListItemDefaults.shapes(
                                shape = if (items.size == 1) shapes.large else shapes.extraSmall,
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerList(
    groups: List<ServerGroup>,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    selectedId: Int? = null,
    onServerSelected: (Int) -> Unit = {},
    onPasteFromClipboard: () -> Unit = {},
    onQrCodeClick: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState()
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = !isLoading && groups.isEmpty(),
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically(
                        initialOffsetY = { 40 },
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
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
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                InfoLayout(
                    icon = Icons.Rounded.NewReleases,
                    title = { stringResource(R.string.empty_server_list) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.m),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.check_subscription),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MaterialTheme.spacing.l,
                                    vertical = MaterialTheme.spacing.xs
                                )
                        ) {
                            Button(
                                onClick = onPasteFromClipboard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MaterialTheme.components.buttonMedium),
                                shape = shapes.extraLarge
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(MaterialTheme.spacing.s))
                                Text(
                                    text = stringResource(R.string.paste_from_clipboard),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            FilledTonalButton(
                                onClick = onQrCodeClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MaterialTheme.components.buttonMedium),
                                shape = shapes.extraLarge
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCode,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(MaterialTheme.spacing.s))
                                Text(
                                    text = stringResource(R.string.qr_code),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically(
                        initialOffsetY = { 40 },
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
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
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(MaterialTheme.components.loadingIndicator))
            }
        }

        AnimatedVisibility(
            visible = !isLoading && groups.isNotEmpty(),
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically(
                        initialOffsetY = { 40 },
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
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
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                groups.forEach { group ->
                    CollapsibleServerList(
                        title = group.title,
                        items = group.items,
                        selectedId = selectedId,
                        onServerSelected = onServerSelected,
                        onRefresh = group.onRefresh,
                        isRefreshing = group.isRefreshing
                    )
                }
            }
        }
    }
}