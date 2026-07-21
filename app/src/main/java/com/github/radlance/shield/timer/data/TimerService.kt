package com.github.radlance.shield.timer.data

import android.os.SystemClock
import com.github.radlance.shield.timer.domain.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

interface TimerService {

    val timerState: StateFlow<TimerState>

    fun start()

    fun stop()
}

internal class BaseTimerService(
    private val coroutineScope: CoroutineScope
) : TimerService {

    private var timerJob: Job? = null

    private val _timerState = MutableStateFlow(TimerState())

    override val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    override fun start() {
        if (_timerState.value.isTimerRunning) return

        cancelTimer()
        _timerState.update {
            it.copy(
                elapsedTime = 0L,
                isTimerRunning = true
            )
        }

        timerJob = coroutineScope.launch {
            val startTime = SystemClock.elapsedRealtime()
            val tickInterval = 100L

            while (isActive) {
                val elapsedMillis = SystemClock.elapsedRealtime() - startTime
                val seconds = elapsedMillis / 1000L

                _timerState.update { it.copy(elapsedTime = seconds) }
                delay(tickInterval.milliseconds)
            }
        }
    }

    override fun stop() {
        cancelTimer()
        _timerState.update {
            it.copy(
                elapsedTime = 0L,
                isTimerRunning = false
            )
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}