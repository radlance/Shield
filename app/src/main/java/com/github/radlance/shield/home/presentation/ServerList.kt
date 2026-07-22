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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.github.radlance.shield.R
import com.github.radlance.shield.uikit.tokens.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerList(
    items: List<MockServerItem>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    emptyMessage: String = stringResource(R.string.empty_server_list)
) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(MaterialTheme.spacing.m)
                )
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
                    SegmentedListItem(
                        supportingContent = {
                            item.description?.let {
                                Text(
                                    it,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        onClick = {},
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