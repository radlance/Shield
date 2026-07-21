package com.github.radlance.shield.alerts.data

import android.view.View
import com.github.radlance.shield.alerts.domain.AlertsRepository
import com.github.radlance.shield.alerts.service.HapticService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LocalAlertsRepository(
    externalScope: CoroutineScope,
    private val alertsDataStore: AlertsDataStore,
    private val hapticService: HapticService,
) : AlertsRepository {
    override val alertConfiguration: StateFlow<AlertsConfiguration> =
        alertsDataStore.alertConfiguration
            .stateIn(
                scope = externalScope,
                started = SharingStarted.Eagerly,
                initialValue = AlertsConfiguration()
            )

    override fun onFocusChanged(view: View?) {
        performHaptic(view)
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        alertsDataStore.setHapticsEnabled(enabled)
    }

    private fun performHaptic(view: View?) {
        if (alertConfiguration.value.hapticsEnabled) {
            hapticService.performHaptic(view)
        }
    }
}