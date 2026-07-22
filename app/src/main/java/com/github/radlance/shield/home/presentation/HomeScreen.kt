package com.github.radlance.shield.home.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
    modifier: Modifier = Modifier
) {
    val timerState by timerViewModel.timerState.collectAsStateWithLifecycle()

    val view = LocalView.current
    val listState = rememberLazyListState()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    val mockServers = remember {
        listOf(
            MockServerItem(1, "Нидерланды", "10 Gbps"),
            MockServerItem(2, "Германия", "10 Gbps"),
            MockServerItem(3, "Финляндия", "1 Gbps"),
            MockServerItem(4, "Швеция", "10 Gbps"),
            MockServerItem(5, "Швейцария", "1 Gbps"),
            MockServerItem(6, "Великобритания", "10 Gbps"),
            MockServerItem(7, "Франция", "10 Gbps"),
            MockServerItem(8, "Польша", "1 Gbps"),
            MockServerItem(9, "США (Восток)", "10 Gbps"),
            MockServerItem(10, "США (Запад)", "10 Gbps"),
            MockServerItem(11, "Канада", "1 Gbps"),
            MockServerItem(12, "Япония", "10 Gbps"),
            MockServerItem(13, "Южная Корея", "1 Gbps"),
            MockServerItem(14, "Сингапур", "10 Gbps"),
            MockServerItem(15, "Австралия", "1 Gbps"),
            MockServerItem(16, "Турция", "1 Gbps"),
            MockServerItem(17, "ОАЭ", "10 Gbps"),
            MockServerItem(18, "Испания", "1 Gbps"),
            MockServerItem(19, "Италия", "1 Gbps"),
            MockServerItem(20, "Норвегия", "10 Gbps")
        )
    }

    val currentServers = if (refreshCount % 4 < 2) mockServers else emptyList()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                ShieldControlBar(
                    isWorking = timerState.isTimerRunning,
                    onStartStop = {
                        if (timerState.isTimerRunning) {
                            timerViewModel.stopTimer(view)
                        } else {
                            timerViewModel.startTimer(view)
                        }
                    },
                    modifier = Modifier.padding(MaterialTheme.spacing.m)
                )

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            delay(1500.milliseconds)
                            refreshCount++
                            isRefreshing = false
                        }
                    },
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    ServerList(
                        items = currentServers,
                        listState = listState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            AddMenu(
                listState = listState,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}