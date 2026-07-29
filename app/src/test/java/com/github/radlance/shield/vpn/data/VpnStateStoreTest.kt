package com.github.radlance.shield.vpn.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnStateStoreTest {
    @Test
    fun activeProfileCanBeStoredAndCleared() = runBlocking {
        val store = DataStoreVpnStateStore(InMemoryPreferencesDataStore())

        assertNull(store.getActiveProfileId())

        store.setActiveProfileId("profile-id")
        assertEquals("profile-id", store.getActiveProfileId())

        store.setActiveProfileId(null)
        assertNull(store.getActiveProfileId())
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (current: Preferences) -> Preferences
        ): Preferences = transform(state.value).also { state.value = it }
    }
}
