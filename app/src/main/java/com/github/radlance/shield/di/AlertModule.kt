package com.github.radlance.shield.di

import com.github.radlance.shield.alerts.data.AlertsDataStore
import com.github.radlance.shield.alerts.data.BaseAlertsDataStore
import com.github.radlance.shield.alerts.data.LocalAlertsRepository
import com.github.radlance.shield.alerts.domain.AlertsRepository
import com.github.radlance.shield.alerts.service.HapticService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val alertModule = module {
    single {
        HapticService(androidContext())
    }

    single {
        BaseAlertsDataStore(
            dataStore = get(named("alerts"))
        )
    }.bind<AlertsDataStore>()

    single<AlertsRepository> {
        LocalAlertsRepository(
            externalScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            alertsDataStore = get(),
            hapticService = get()
        )
    }
}