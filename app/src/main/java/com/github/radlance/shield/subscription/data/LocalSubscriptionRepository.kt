package com.github.radlance.shield.subscription.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.radlance.shield.subscription.domain.ImportResult
import com.github.radlance.shield.subscription.domain.ProfileParser
import com.github.radlance.shield.subscription.domain.Subscription
import com.github.radlance.shield.subscription.domain.SubscriptionGroup
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.subscription.domain.SubscriptionSource
import com.github.radlance.shield.subscription.domain.VlessProfile
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.subscriptionStore by preferencesDataStore(name = "subscriptions")

class LocalSubscriptionRepository(
    private val context: Context,
    private val parser: ProfileParser,
    private val cipher: SecretCipher,
    private val downloader: SubscriptionDownloader
) : SubscriptionRepository {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val state: Flow<StoredState> = context.subscriptionStore.data.map { preferences ->
        preferences[STATE_KEY]
            ?.let { encrypted -> runCatching { decode(encrypted) }.getOrNull() }
            ?: StoredState()
    }

    override val groups: Flow<List<SubscriptionGroup>> = state.map { stored ->
        stored.subscriptions.map { subscription ->
            SubscriptionGroup(
                subscription = subscription,
                profiles = stored.profiles.filter { it.subscriptionId == subscription.id }
            )
        }
    }

    override val selectedProfileId: Flow<String?> = state.map { it.selectedProfileId }

    override suspend fun import(
        name: String,
        source: SubscriptionSource
    ): Result<Subscription> = runCatching {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val (sourceUrl, body) = when (source) {
            is SubscriptionSource.Direct -> null to source.link
            is SubscriptionSource.Remote -> {
                validateSubscriptionUrl(source.url)
                source.url to downloadSubscription(source.url)
            }
        }
        val parsed = parser.parseSubscription(body, id)
        requireSupportedProfiles(parsed)
        val subscription = Subscription(
            id = id,
            name = name.ifBlank { defaultName(sourceUrl, parsed.profiles) },
            sourceUrl = sourceUrl,
            createdAtEpochMillis = now,
            lastUpdatedAtEpochMillis = now
        )
        mutate { current ->
            current.copy(
                subscriptions = current.subscriptions + subscription,
                profiles = current.profiles + parsed.profiles,
                selectedProfileId = current.selectedProfileId ?: parsed.profiles.first().id
            )
        }
        subscription
    }

    override suspend fun refresh(subscriptionId: String): Result<Unit> {
        val result = runCatching {
            val current = state.first()
            val subscription = current.subscriptions.firstOrNull { it.id == subscriptionId }
                ?: error("Subscription not found")
            val url = subscription.sourceUrl ?: return@runCatching
            val content = downloadSubscription(url)
            val parsed = parser.parseSubscription(content, subscriptionId)
            requireSupportedProfiles(parsed)
            val now = System.currentTimeMillis()
            mutate { latest ->
                val otherProfiles = latest.profiles.filterNot { it.subscriptionId == subscriptionId }
                val selected = latest.selectedProfileId
                val nextSelection = when {
                    selected == null -> parsed.profiles.first().id
                    parsed.profiles.any { it.id == selected } -> selected
                    latest.profiles.any { it.id == selected && it.subscriptionId != subscriptionId } -> selected
                    else -> parsed.profiles.first().id
                }
                latest.copy(
                    subscriptions = latest.subscriptions.map {
                        if (it.id == subscriptionId) {
                            it.copy(lastUpdatedAtEpochMillis = now, lastError = null)
                        } else {
                            it
                        }
                    },
                    profiles = otherProfiles + parsed.profiles,
                    selectedProfileId = nextSelection
                )
            }
        }
        result.exceptionOrNull()?.let { failure ->
            mutate { current ->
                current.copy(
                    subscriptions = current.subscriptions.map {
                        if (it.id == subscriptionId) {
                            it.copy(lastError = failure.message ?: "Refresh failed")
                        } else {
                            it
                        }
                    }
                )
            }
        }
        return result
    }

    override suspend fun refreshAll(): List<Result<Unit>> =
        state.first().subscriptions
            .filter { it.sourceUrl != null }
            .map { refresh(it.id) }

    override suspend fun delete(subscriptionId: String) {
        mutate { current ->
            val remainingProfiles = current.profiles.filterNot { it.subscriptionId == subscriptionId }
            current.copy(
                subscriptions = current.subscriptions.filterNot { it.id == subscriptionId },
                profiles = remainingProfiles,
                selectedProfileId = current.selectedProfileId
                    ?.takeIf { selected -> remainingProfiles.any { it.id == selected } }
                    ?: remainingProfiles.firstOrNull()?.id
            )
        }
    }

    override suspend fun selectProfile(profileId: String) {
        mutate { current ->
            require(current.profiles.any { it.id == profileId }) { "Profile not found" }
            current.copy(selectedProfileId = profileId)
        }
    }

    override suspend fun getProfile(profileId: String): VlessProfile? =
        state.first().profiles.firstOrNull { it.id == profileId }

    private suspend fun mutate(transform: (StoredState) -> StoredState) {
        mutex.withLock {
            context.subscriptionStore.edit { preferences ->
                val current = preferences[STATE_KEY]
                    ?.let { encrypted -> runCatching { decode(encrypted) }.getOrNull() }
                    ?: StoredState()
                preferences[STATE_KEY] = encode(transform(current))
            }
        }
    }

    private suspend fun downloadSubscription(url: String): String {
        return downloader.download(url).validatedBody()
    }

    private fun validateSubscriptionUrl(url: String) {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) { "Subscription URL must use HTTPS" }
        require(!uri.host.isNullOrBlank()) { "Subscription URL has no host" }
    }

    private fun defaultName(url: String?, profiles: List<VlessProfile>): String =
        url?.let { runCatching { URI(it).host }.getOrNull() }
            ?: profiles.firstOrNull()?.name
            ?: "Subscription"

    private fun requireSupportedProfiles(result: ImportResult) {
        if (result.profiles.isNotEmpty()) return
        if (result.unsupportedTransports.isNotEmpty()) {
            val transports = result.unsupportedTransports.sorted().joinToString()
            throw IllegalArgumentException(
                "The subscription only contains unsupported VLESS transports: $transports"
            )
        }
        throw IllegalArgumentException("No supported VLESS profiles were found")
    }

    private fun encode(state: StoredState): String =
        cipher.encrypt(json.encodeToString(StoredState.serializer(), state))

    private fun decode(value: String): StoredState =
        json.decodeFromString(StoredState.serializer(), cipher.decrypt(value))

    @Serializable
    private data class StoredState(
        val subscriptions: List<Subscription> = emptyList(),
        val profiles: List<VlessProfile> = emptyList(),
        val selectedProfileId: String? = null
    )

    private companion object {
        val STATE_KEY = stringPreferencesKey("encrypted_state_v1")
    }
}
