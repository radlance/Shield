package com.github.radlance.shield.vpn.routing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingSettingsTest {
    @Test
    fun defaultsToRussianServicesDirect() = runBlocking {
        val repository = DataStoreRoutingSettingsRepository(InMemoryPreferencesDataStore())

        val settings = repository.settings.first()

        assertTrue(settings.smartRussianRouting)
        assertTrue(settings.forceDirectDomains.isEmpty())
        assertTrue(settings.forceProxyDomains.isEmpty())
    }

    @Test
    fun storesRoutingOverrides() = runBlocking {
        val repository = DataStoreRoutingSettingsRepository(InMemoryPreferencesDataStore())
        val expected = RoutingSettings(
            smartRussianRouting = false,
            forceDirectDomains = setOf("bank.example"),
            forceProxyDomains = setOf("blocked.example")
        )

        repository.update(expected)

        assertEquals(expected, repository.settings.first())
    }

    @Test
    fun normalizesDomainsAndGivesProxyPrecedence() = runBlocking {
        val repository = DataStoreRoutingSettingsRepository(InMemoryPreferencesDataStore())
        repository.update(
            RoutingSettings(
                forceDirectDomains = setOf(
                    " HTTPS://ГОСУСЛУГИ.РФ/path ",
                    "*.Bank.Example",
                    "blocked.example"
                ),
                forceProxyDomains = setOf("BLOCKED.EXAMPLE", "invalid")
            )
        )

        val settings = repository.settings.first()

        assertEquals(
            setOf("xn--c1aapkosapc.xn--p1ai", "bank.example"),
            settings.forceDirectDomains
        )
        assertEquals(setOf("blocked.example"), settings.forceProxyDomains)
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (current: Preferences) -> Preferences
        ): Preferences = transform(state.value).also { state.value = it }
    }
}
