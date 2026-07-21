package com.github.radlance.shield.timer.data

import com.github.radlance.shield.timer.domain.TimerRepository
import com.github.radlance.shield.timer.domain.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LocalTimerRepository(
    private val timerService: TimerService,
    externalScope: CoroutineScope
) : TimerRepository {
    override val timerState: StateFlow<TimerState> = timerService.timerState
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = TimerState()
        )

    override fun start() {
        timerService.start()
    }

    override fun stop() {
        timerService.stop()
    }
}