package com.github.radlance.shield.timer.presentation

import android.view.View
import androidx.lifecycle.ViewModel
import com.github.radlance.shield.alerts.domain.AlertsRepository
import com.github.radlance.shield.timer.domain.TimerRepository

class TimerViewModel(
    private val timerRepository: TimerRepository,
    private val alertsRepository: AlertsRepository
) : ViewModel() {

    val timerState = timerRepository.timerState

    fun startTimer(view: View? = null) {
        if (timerState.value.isTimerRunning) return
        timerRepository.start()
        alertsRepository.onFocusChanged(view)
    }

    fun stopTimer(view: View? = null) {
        if (!timerState.value.isTimerRunning) return
        timerRepository.stop()
        alertsRepository.onFocusChanged(view)
    }
}