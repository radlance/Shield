package com.github.radlance.shield.timer.presentation

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.radlance.shield.alerts.domain.AlertsRepository
import com.github.radlance.shield.timer.domain.TimerRepository
import kotlinx.coroutines.launch

class TimerViewModel(
    private val timerRepository: TimerRepository,
    private val alertsRepository: AlertsRepository
) : ViewModel() {

    val timerState = timerRepository.timerState

    fun startTimer(view: View? = null) {
        if (timerState.value.isTimerRunning) return
        viewModelScope.launch {
            timerRepository.start()
            alertsRepository.onFocusStart(view)
        }
    }

    fun stopTimer(view: View? = null) {
        if (!timerState.value.isTimerRunning) return
        viewModelScope.launch {
            timerRepository.stop()
            alertsRepository.onFocusStop(view)
        }
    }
}