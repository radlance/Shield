package com.github.radlance.shield.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.preferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val Context.alertsDataStore: DataStore<Preferences> by preferencesDataStore(name = "alerts")
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme")
private val Context.vpnStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vpn_state",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "vpn_service"))
    }
)

val dataStoreModule = module {
    single<DataStore<Preferences>>(named("theme")) {
        androidContext().themeDataStore
    }

    single<DataStore<Preferences>>(named("alerts")) {
        androidContext().alertsDataStore
    }

    single<DataStore<Preferences>>(named("vpn_state")) {
        androidContext().vpnStateDataStore
    }
}
