package com.github.radlance.shield.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.timer.presentation.TimerViewModel

@Composable
fun HomeScreen(
    timerViewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val timerState by timerViewModel.timerState.collectAsStateWithLifecycle()

    val view = LocalView.current
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ShieldControlBar(
                    isWorking = timerState.isTimerRunning,
                    onStartStop = {
                        if (timerState.isTimerRunning) {
                            timerViewModel.stopTimer(view)
                        } else {
                            timerViewModel.startTimer(view)
                        }
                    }
                )
            }
        }
    }
}