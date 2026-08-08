package com.github.radlance.shield.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.radlance.shield.subscription.domain.SubscriptionAccessStatus
import com.github.radlance.shield.subscription.domain.SubscriptionGroup
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.subscription.domain.SubscriptionSource
import com.github.radlance.shield.subscription.domain.isSupportedProxyLink
import com.github.radlance.shield.subscription.domain.accessStatus
import com.github.radlance.shield.vpn.domain.ServerLatencyTester
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import com.github.radlance.shield.vpn.domain.VpnController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.supervisorScope

data class HomeUiState(
    val groups: List<SubscriptionGroup> = emptyList(),
    val selectedProfileId: String? = null,
    val connectionState: VpnConnectionState = VpnConnectionState.Disconnected,
    val busySubscriptionIds: Set<String> = emptySet(),
    val pingingSubscriptionIds: Set<String> = emptySet(),
    val serverLatencies: Map<String, ServerLatency> = emptyMap(),
    val isImporting: Boolean = false,
    val isInitialized: Boolean = false,
    val message: String? = null
)

class HomeViewModel(
    private val repository: SubscriptionRepository,
    private val vpnController: VpnController,
    private val latencyTester: ServerLatencyTester
) : ViewModel() {
    private val busySubscriptionIds = MutableStateFlow<Set<String>>(emptySet())
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pingingSubscriptionIds = MutableStateFlow<Set<String>>(emptySet())
    private val serverLatencies = MutableStateFlow<Map<String, ServerLatency>>(emptyMap())
    private val pingJobs = mutableMapOf<String, Job>()
    private val pingPermits = Semaphore(MAX_CONCURRENT_PINGS)

    private val baseState = combine(
        repository.groups,
        repository.selectedProfileId,
        vpnController.state,
        busySubscriptionIds,
        importing
    ) { groups, selected, connection, busy, isImporting ->
        HomeUiState(
            groups = groups,
            selectedProfileId = selected,
            connectionState = connection,
            busySubscriptionIds = busy,
            isImporting = isImporting,
            isInitialized = true
        )
    }

    private val stateWithPings = combine(
        baseState,
        pingingSubscriptionIds,
        serverLatencies
    ) { state, pingingIds, latencies ->
        state.copy(
            pingingSubscriptionIds = pingingIds,
            serverLatencies = latencies
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        stateWithPings,
        message
    ) { state, currentMessage ->
        state.copy(message = currentMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun import(name: String, value: String) {
        if (value.isBlank() || importing.value) return
        viewModelScope.launch {
            importing.value = true
            message.value = null
            val source = if (value.trim().isSupportedProxyLink()) {
                SubscriptionSource.Direct(value.trim())
            } else {
                SubscriptionSource.Remote(value.trim())
            }
            repository.import(name.trim(), source)
                .onFailure { message.value = it.message ?: "Import failed" }
            importing.value = false
        }
    }

    fun selectProfile(profileId: String) {
        val state = uiState.value
        unavailableMessage(profileId, state.groups)?.let {
            message.value = it
            return
        }
        val connection = state.connectionState
        if (
            connection is VpnConnectionState.Connected ||
            connection is VpnConnectionState.Connecting ||
            connection is VpnConnectionState.Reconnecting ||
            connection is VpnConnectionState.Disconnecting
        ) {
            val activeProfileId = (connection as? VpnConnectionState.Connected)
                ?.profileId
                ?: state.selectedProfileId
            if (activeProfileId != profileId) {
                message.value = null
                vpnController.switchProfile(profileId)
            }
            return
        }
        viewModelScope.launch {
            runCatching { repository.selectProfile(profileId) }
                .onFailure { message.value = it.message }
        }
    }

    fun refresh(subscriptionId: String) {
        if (subscriptionId in busySubscriptionIds.value) return
        viewModelScope.launch {
            busySubscriptionIds.value += subscriptionId
            repository.refresh(subscriptionId)
                .onFailure { message.value = it.message ?: "Refresh failed" }
            busySubscriptionIds.value -= subscriptionId
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            repository.refreshAll().firstNotNullOfOrNull(Result<Unit>::exceptionOrNull)
                ?.let { message.value = it.message ?: "Refresh failed" }
        }
    }

    fun pingSubscription(subscriptionId: String) {
        if (pingJobs[subscriptionId]?.isActive == true) return
        val profiles = uiState.value.groups
            .firstOrNull { it.subscription.id == subscriptionId }
            ?.profiles
            .orEmpty()
        if (profiles.isEmpty()) return

        val profileIds = profiles.mapTo(linkedSetOf()) { it.id }
        serverLatencies.update { current ->
            current + profileIds.associateWith { ServerLatency.Pinging }
        }
        pingingSubscriptionIds.update { it + subscriptionId }

        val job = viewModelScope.launch {
            try {
                supervisorScope {
                    profiles.map { profile ->
                        async {
                            val latency = pingPermits.withPermit {
                                try {
                                    latencyTester.measure(profile)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            val state = latency
                                ?.let(ServerLatency::Available)
                                ?: ServerLatency.Unavailable
                            serverLatencies.update {
                                it + (profile.id to state)
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                serverLatencies.update { current ->
                    current.mapValues { (profileId, state) ->
                        if (profileId in profileIds && state == ServerLatency.Pinging) {
                            ServerLatency.Idle
                        } else {
                            state
                        }
                    }
                }
                pingingSubscriptionIds.update { it - subscriptionId }
                pingJobs.remove(subscriptionId)
            }
        }
        pingJobs[subscriptionId] = job
    }

    fun delete(subscriptionId: String) {
        viewModelScope.launch {
            val group = uiState.value.groups.firstOrNull { it.subscription.id == subscriptionId }
            pingJobs.remove(subscriptionId)?.cancel()
            val profileIds = group?.profiles?.mapTo(hashSetOf()) { it.id }.orEmpty()
            serverLatencies.update { it - profileIds }
            val selectedBelongsToGroup = group?.profiles?.any {
                it.id == uiState.value.selectedProfileId
            } == true
            if (selectedBelongsToGroup && uiState.value.connectionState is VpnConnectionState.Connected) {
                vpnController.disconnect()
            }
            repository.delete(subscriptionId)
        }
    }

    fun connectSelected() {
        val id = uiState.value.selectedProfileId ?: return
        unavailableMessage(id, uiState.value.groups)?.let {
            message.value = it
            return
        }
        vpnController.connect(id)
    }

    fun disconnect() = vpnController.disconnect()
    fun dismissMessage() {
        message.value = null
    }

    private fun unavailableMessage(
        profileId: String,
        groups: List<SubscriptionGroup>
    ): String? {
        val subscription = groups
            .firstOrNull { group -> group.profiles.any { it.id == profileId } }
            ?.subscription
            ?: return null
        return when (
            subscription.metadata.accessStatus(System.currentTimeMillis() / 1_000)
        ) {
            SubscriptionAccessStatus.AVAILABLE -> null
            SubscriptionAccessStatus.EXPIRED -> "The subscription has expired"
            SubscriptionAccessStatus.TRAFFIC_EXHAUSTED ->
                "The subscription traffic limit has been reached"
        }
    }

    private companion object {
        const val MAX_CONCURRENT_PINGS = 8
    }
}
