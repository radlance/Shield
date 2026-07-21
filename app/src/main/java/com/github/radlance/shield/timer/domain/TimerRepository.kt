package com.github.radlance.shield.timer.domain

import kotlinx.coroutines.flow.StateFlow

interface TimerRepository {

    val timerState: StateFlow<TimerState>

    fun start()

    fun stop()
}