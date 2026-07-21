package com.github.radlance.shield.alerts.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface AlertsDataStore {

    val alertConfiguration: Flow<AlertsConfiguration>

    suspend fun setHapticsEnabled(enabled: Boolean)
}

class BaseAlertsDataStore(
    private val dataStore: DataStore<Preferences>
) : AlertsDataStore {
    private val hapticsKey = booleanPreferencesKey("haptics_enabled")

    override val alertConfiguration: Flow<AlertsConfiguration> = dataStore.data
        .catch {
            emit(emptyPreferences())
        }
        .map { preferences ->
            AlertsConfiguration(
                hapticsEnabled = preferences[hapticsKey] ?: true
            )
        }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[hapticsKey] = enabled }
    }
}