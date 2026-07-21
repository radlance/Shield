package com.github.radlance.shield.alerts.domain

import android.view.View
import com.github.radlance.shield.alerts.data.AlertsConfiguration
import kotlinx.coroutines.flow.StateFlow

interface AlertsRepository {

    val alertConfiguration: StateFlow<AlertsConfiguration>

    fun onFocusStart(view: View? = null)

    fun onFocusStop(view: View? = null)

    suspend fun setHapticsEnabled(enabled: Boolean)
}