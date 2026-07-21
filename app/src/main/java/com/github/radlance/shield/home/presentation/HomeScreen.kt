package com.github.radlance.shield.home.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.timer.presentation.TimerViewModel
import com.github.radlance.shield.uikit.tokens.spacing

@Composable
fun HomeScreen(
    timerViewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val timerState by timerViewModel.timerState.collectAsStateWithLifecycle()

    val view = LocalView.current
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()


    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
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
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(count = 50) {
                        Text(it.toString())
                    }
                }
            }

            AddMenu(
                listState = listState,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}