package com.github.radlance.shield.home.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.timer.presentation.TimerViewModel
import com.github.radlance.shield.uikit.tokens.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    timerViewModel: TimerViewModel,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val timerState by timerViewModel.timerState.collectAsStateWithLifecycle()

    val view = LocalView.current
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()

    val primaryServers = remember {
        listOf(
            MockServerItem(1, "🇳🇱", "Нидерланды", "10 Gbps"),
            MockServerItem(2, "🇩🇪", "Германия", "10 Gbps"),
            MockServerItem(3, "🇫🇮", "Финляндия", "1 Gbps"),
            MockServerItem(4, "🇸🇪", "Швеция", "10 Gbps"),
            MockServerItem(5, "🇨🇭", "Швейцария", "1 Gbps"),
            MockServerItem(6, "🇬🇧", "Великобритания", "10 Gbps"),
            MockServerItem(7, "🇫🇷", "Франция", "10 Gbps"),
            MockServerItem(8, "🇵🇱", "Польша", "1 Gbps"),
            MockServerItem(9, "🇺🇸", "США (Восток)", "10 Gbps"),
            MockServerItem(10, "🇺🇸", "США (Запад)", "10 Gbps")
        )
    }

    val backupServers = remember {
        listOf(
            MockServerItem(11, "🇨🇦", "Канада", "1 Gbps"),
            MockServerItem(12, "🇯🇵", "Япония", "10 Gbps"),
            MockServerItem(13, "🇰🇷", "Южная Корея", "1 Gbps"),
            MockServerItem(14, "🇸🇬", "Сингапур", "10 Gbps"),
            MockServerItem(15, "🇦🇺", "Австралия", "1 Gbps")
        )
    }

    val initialMockGroups = remember(primaryServers, backupServers) {
        listOf(
            ServerGroup(
                title = "Основная подписка",
                items = primaryServers,
                onRefresh = {}
            ),
            ServerGroup(
                title = "Резервные серверы",
                items = backupServers,
                onRefresh = {}
            )
        )
    }

    var serverGroups by remember {
        mutableStateOf<List<ServerGroup>>(emptyList())
    }

    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val handleAddServers: () -> Unit = {
        if (!isLoading) {
            scope.launch {
                isLoading = true
                delay(1500.milliseconds)
                serverGroups = initialMockGroups
                isLoading = false
            }
        }
    }

    var selectedServerId by remember { mutableStateOf<Int?>(1) }

    val isAtTop by remember {
        derivedStateOf {
            scrollState.value == 0
        }
    }

    LaunchedEffect(isAtTop, scrollBehavior) {
        snapshotFlow {
            isAtTop to scrollBehavior?.state?.contentOffset
        }.collect { (atTop, offset) ->
            if (atTop && offset != null && offset != 0f) {
                scrollBehavior?.state?.contentOffset = 0f
            }
        }
    }

    val customNestedScrollConnection = remember(scrollBehavior, isAtTop) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val consumed = scrollBehavior?.nestedScrollConnection?.onPreScroll(available, source) ?: Offset.Zero
                if (isAtTop && scrollBehavior?.state?.contentOffset != 0f) {
                    scrollBehavior?.state?.contentOffset = 0f
                }
                return consumed
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val result = scrollBehavior?.nestedScrollConnection?.onPostScroll(consumed, available, source) ?: Offset.Zero
                if (isAtTop && scrollBehavior?.state?.contentOffset != 0f) {
                    scrollBehavior?.state?.contentOffset = 0f
                }
                return result
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val consumed = scrollBehavior?.nestedScrollConnection?.onPreFling(available) ?: Velocity.Zero
                if (isAtTop && scrollBehavior?.state?.contentOffset != 0f) {
                    scrollBehavior?.state?.contentOffset = 0f
                }
                return consumed
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val result = scrollBehavior?.nestedScrollConnection?.onPostFling(consumed, available) ?: Velocity.Zero
                if (isAtTop && scrollBehavior?.state?.contentOffset != 0f) {
                    scrollBehavior?.state?.contentOffset = 0f
                }
                return result
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(customNestedScrollConnection),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                ShieldControlBar(
                    isWorking = timerState.isTimerRunning,
                    enabled = serverGroups.isNotEmpty(),
                    onStartStop = {
                        if (timerState.isTimerRunning) {
                            timerViewModel.stopTimer(view)
                        } else {
                            timerViewModel.startTimer(view)
                        }
                    },
                    modifier = Modifier.padding(MaterialTheme.spacing.m)
                )

                ServerList(
                    groups = serverGroups,
                    isLoading = isLoading,
                    selectedId = selectedServerId,
                    onServerSelected = { selectedServerId = it },
                    onPasteFromClipboard = handleAddServers,
                    onQrCodeClick = handleAddServers,
                    scrollState = scrollState,
                    modifier = Modifier.weight(1f)
                )
            }

            AddMenu(
                listState = listState,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}