package com.github.radlance.shield.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.github.radlance.shield.R
import com.github.radlance.shield.common.presentation.InfoLayout
import com.github.radlance.shield.uikit.tokens.components
import com.github.radlance.shield.uikit.tokens.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerList(
    items: List<MockServerItem>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    var selectedId by remember(items) { mutableStateOf(items.firstOrNull()?.id) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = items.isEmpty(),
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically(
                        initialOffsetY = { 40 },
                        animationSpec = spring(
                            dampingRatio = 0.65f,
                            stiffness = Spring.StiffnessLow
                        )
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
            InfoLayout(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                icon = Icons.Rounded.NewReleases,
                title = { stringResource(R.string.empty_server_list) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.m),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.check_subscription,
                        ),
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
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MaterialTheme.components.buttonMedium),
                            shapes = ButtonDefaults.shapes()
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
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MaterialTheme.components.buttonMedium),
                            shapes = ButtonDefaults.shapes()
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

        AnimatedVisibility(
            visible = items.isNotEmpty(),
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically(
                        initialOffsetY = { 40 },
                        animationSpec = spring(
                            dampingRatio = 0.65f,
                            stiffness = Spring.StiffnessLow
                        )
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MaterialTheme.spacing.m,
                    end = MaterialTheme.spacing.m,
                    bottom = MaterialTheme.spacing.s
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)
            ) {
                itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
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
                                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                                exit = scaleOut(
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
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
                        onClick = { selectedId = item.id },
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