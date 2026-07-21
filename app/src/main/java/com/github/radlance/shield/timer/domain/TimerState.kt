package com.github.radlance.shield.timer.domain

data class TimerState(
    val elapsedTime: Long = 0L,
    val isTimerRunning: Boolean = false
) {
    val isIdle: Boolean
        get() = !isTimerRunning && elapsedTime == 0L
}