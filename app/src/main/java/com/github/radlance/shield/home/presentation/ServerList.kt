package com.github.radlance.shield.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.radlance.shield.home.presentation.components.CollapsibleServerList
import com.github.radlance.shield.home.presentation.components.EmptyServerList
import com.github.radlance.shield.home.presentation.components.ServerListTransitions
import com.github.radlance.shield.uikit.tokens.components

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