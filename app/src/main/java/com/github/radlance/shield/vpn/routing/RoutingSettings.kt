package com.github.radlance.shield.vpn.routing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.IDN

data class RoutingSettings(
    val smartRussianRouting: Boolean = true,
    val forceDirectDomains: Set<String> = emptySet(),
    val forceProxyDomains: Set<String> = emptySet()
)

interface RoutingSettingsRepository {
    val settings: Flow<RoutingSettings>

    suspend fun update(settings: RoutingSettings)
}

class DataStoreRoutingSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : RoutingSettingsRepository {
    override val settings: Flow<RoutingSettings> = dataStore.data.map { preferences ->
        RoutingSettings(
            smartRussianRouting = preferences[SMART_RUSSIAN_ROUTING] ?: true,
            forceDirectDomains = preferences[FORCE_DIRECT_DOMAINS].orEmpty().toSet(),
            forceProxyDomains = preferences[FORCE_PROXY_DOMAINS].orEmpty().toSet()
        )
    }

    override suspend fun update(settings: RoutingSettings) {
        val proxyDomains = normalizeDomainOverrides(settings.forceProxyDomains)
        val directDomains =
            normalizeDomainOverrides(settings.forceDirectDomains) - proxyDomains
        dataStore.edit { preferences ->
            preferences[SMART_RUSSIAN_ROUTING] = settings.smartRussianRouting
            preferences[FORCE_DIRECT_DOMAINS] = directDomains
            preferences[FORCE_PROXY_DOMAINS] = proxyDomains
        }
    }

    private companion object {
        // Keep the original boolean key so existing installations retain their preference.
        val SMART_RUSSIAN_ROUTING = booleanPreferencesKey("russian_services_direct")
        val FORCE_DIRECT_DOMAINS = stringSetPreferencesKey("force_direct_domains")
        val FORCE_PROXY_DOMAINS = stringSetPreferencesKey("force_proxy_domains")
    }
}

internal fun normalizeDomainOverrides(domains: Iterable<String>): Set<String> =
    domains.mapNotNullTo(linkedSetOf()) { raw ->
        val candidate = raw
            .trim()
            .lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .removePrefix("*.")
            .trim('.')
        if (candidate.isBlank() || candidate.any(Char::isWhitespace)) {
            null
        } else {
            runCatching { IDN.toASCII(candidate) }
                .getOrNull()
                ?.takeIf { it.length <= 253 && '.' in it }
        }
    }
