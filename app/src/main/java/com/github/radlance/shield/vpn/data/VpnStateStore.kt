package com.github.radlance.shield.vpn.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

interface VpnStateStore {
    suspend fun getActiveProfileId(): String?
    suspend fun setActiveProfileId(profileId: String?)
}

class DataStoreVpnStateStore(
    private val dataStore: DataStore<Preferences>
) : VpnStateStore {
    override suspend fun getActiveProfileId(): String? =
        dataStore.data.first()[activeProfileKey]

    override suspend fun setActiveProfileId(profileId: String?) {
        dataStore.edit { preferences ->
            if (profileId == null) {
                preferences.remove(activeProfileKey)
            } else {
                preferences[activeProfileKey] = profileId
            }
        }
    }

    private companion object {
        val activeProfileKey = stringPreferencesKey("active_profile")
    }
}
