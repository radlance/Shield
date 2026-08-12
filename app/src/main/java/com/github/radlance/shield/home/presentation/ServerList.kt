package com.github.radlance.shield.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.github.radlance.shield.R
import com.github.radlance.shield.home.presentation.components.CollapsibleServerList
import com.github.radlance.shield.home.presentation.components.EmptyServerList
import com.github.radlance.shield.home.presentation.components.ServerListTransitions
import com.github.radlance.shield.uikit.tokens.components
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerList(
    groups: List<ServerGroup>,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    selectedId: String? = null,
    onServerSelected: (String) -> Unit = {},
    onPasteFromClipboard: () -> Unit = {},
    onQrCodeClick: () -> Unit = {},
    onPinnedOrderChanged: (List<String>) -> Unit = {},
    scrollState: LazyListState = rememberLazyListState()
) {
    val sourceOrder = groups.map(ServerGroup::id)
    val sourcePinnedIds = groups.filter(ServerGroup::isPinned).map(ServerGroup::id)
    val currentSourceOrder = rememberUpdatedState(sourceOrder)
    val currentPinnedIds = rememberUpdatedState(sourcePinnedIds)
    val currentOnPinnedOrderChanged = rememberUpdatedState(onPinnedOrderChanged)
    val displayedOrder = remember(scrollState) { mutableStateOf<List<String>?>(null) }
    val overrideSourceOrder = remember(scrollState) { mutableStateOf<List<String>?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        listState = scrollState,
        canDragOver = { draggedOver, dragging ->
            val pinnedIds = currentPinnedIds.value.toSet()
            draggedOver.key in pinnedIds && dragging.key in pinnedIds
        },
        onMove = { from, to ->
            val order = displayedOrder.value ?: currentSourceOrder.value
            if (from.index !in order.indices || to.index !in order.indices) {
                return@rememberReorderableLazyListState
            }

            val pinnedIds = currentPinnedIds.value.toSet()
            if (order[from.index] !in pinnedIds || order[to.index] !in pinnedIds) {
                return@rememberReorderableLazyListState
            }

            if (displayedOrder.value == null) {
                overrideSourceOrder.value = currentSourceOrder.value
            }
            displayedOrder.value = order.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
        onDragEnd = { _, _ ->
            val sourcePinnedOrder = currentPinnedIds.value
            val pinnedIds = sourcePinnedOrder.toSet()
            val finalPinnedOrder = (displayedOrder.value ?: currentSourceOrder.value)
                .filter { it in pinnedIds }
            if (
                finalPinnedOrder.size == sourcePinnedOrder.size &&
                finalPinnedOrder.toSet() == pinnedIds &&
                finalPinnedOrder != sourcePinnedOrder
            ) {
                currentOnPinnedOrderChanged.value(finalPinnedOrder)
            }
        }
    )

    val groupsById = groups.associateBy(ServerGroup::id)
    val orderedGroups = (displayedOrder.value ?: sourceOrder)
        .mapNotNull(groupsById::get)
        .plus(groups.filter { it.id !in (displayedOrder.value ?: sourceOrder) })
    val pinnedIds = orderedGroups.filter(ServerGroup::isPinned).map(ServerGroup::id)
    val canReorder = pinnedIds.size > 1
    val reorderDescription = stringResource(R.string.reorder_subscription)
    val moveUpDescription = stringResource(R.string.move_subscription_up)
    val moveDownDescription = stringResource(R.string.move_subscription_down)

    LaunchedEffect(sourceOrder, sourcePinnedIds, reorderableState.draggingItemKey) {
        if (reorderableState.draggingItemKey != null) return@LaunchedEffect

        val override = displayedOrder.value ?: return@LaunchedEffect
        val sourceAtStart = overrideSourceOrder.value
        val sourceChanged = sourceAtStart != null && sourceAtStart != sourceOrder
        val invalidOverride = override.size != sourceOrder.size ||
            override.toSet() != sourceOrder.toSet() ||
            override.filter { groupsById[it]?.isPinned == true }.toSet() != sourcePinnedIds.toSet()

        if (override == sourceOrder || sourceChanged || invalidOverride) {
            displayedOrder.value = null
            overrideSourceOrder.value = null
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = !isLoading && groups.isEmpty(),
            enter = ServerListTransitions.enterTransition,
            exit = ServerListTransitions.exitTransition,
            modifier = Modifier.fillMaxSize()
        ) {
            EmptyServerList(
                onPasteFromClipboard = onPasteFromClipboard,
                onQrCodeClick = onQrCodeClick
            )
        }

        AnimatedVisibility(
            visible = isLoading,
            enter = ServerListTransitions.enterTransition,
            exit = ServerListTransitions.exitTransition,
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
            enter = ServerListTransitions.enterTransition,
            exit = ServerListTransitions.exitTransition,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = reorderableState.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .reorderable(reorderableState)
            ) {
                items(
                    items = orderedGroups,
                    key = ServerGroup::id
                ) { group ->
                    ReorderableItem(
                        state = reorderableState,
                        key = group.id,
                        defaultDraggingModifier = Modifier.animateItem()
                    ) { isDragging ->
                        val pinnedIndex = pinnedIds.indexOf(group.id)
                        val dragHandleModifier = if (group.isPinned && canReorder) {
                            Modifier
                                .testTag("pinned_drag_handle_${group.id}")
                                .detectReorder(reorderableState)
                                .semantics {
                                    contentDescription = reorderDescription
                                    customActions = buildList {
                                        if (pinnedIndex > 0) {
                                            add(
                                                CustomAccessibilityAction(moveUpDescription) {
                                                    val next = pinnedIds.toMutableList()
                                                    next.add(
                                                        pinnedIndex - 1,
                                                        next.removeAt(pinnedIndex)
                                                    )
                                                    currentOnPinnedOrderChanged.value(next)
                                                    true
                                                }
                                            )
                                        }
                                        if (pinnedIndex in 0..<pinnedIds.lastIndex) {
                                            add(
                                                CustomAccessibilityAction(moveDownDescription) {
                                                    val next = pinnedIds.toMutableList()
                                                    next.add(
                                                        pinnedIndex + 1,
                                                        next.removeAt(pinnedIndex)
                                                    )
                                                    currentOnPinnedOrderChanged.value(next)
                                                    true
                                                }
                                            )
                                        }
                                    }
                                }
                        } else {
                            null
                        }

                        CollapsibleServerList(
                            title = group.title,
                            items = group.items,
                            metadata = group.metadata,
                            accessStatus = group.accessStatus,
                            isPinned = group.isPinned,
                            selectedId = selectedId,
                            onServerSelected = onServerSelected,
                            onRefresh = group.onRefresh,
                            onPing = group.onPing,
                            onTogglePin = group.onTogglePin,
                            onDelete = group.onDelete,
                            isRefreshing = group.isRefreshing,
                            isPinging = group.isPinging,
                            error = group.error,
                            dragHandleModifier = dragHandleModifier,
                            isDragging = isDragging
                        )
                    }
                }
            }
        }
    }
}
