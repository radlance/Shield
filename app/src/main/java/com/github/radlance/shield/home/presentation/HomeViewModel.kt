package com.github.radlance.shield.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.radlance.shield.subscription.domain.SubscriptionGroup
import com.github.radlance.shield.subscription.domain.SubscriptionAccessStatus
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.subscription.domain.SubscriptionSource
import com.github.radlance.shield.subscription.domain.accessStatus
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import com.github.radlance.shield.vpn.domain.VpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val groups: List<SubscriptionGroup> = emptyList(),
    val selectedProfileId: String? = null,
    val connectionState: VpnConnectionState = VpnConnectionState.Disconnected,
    val busySubscriptionIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val isInitialized: Boolean = false,
    val message: String? = null
)

class HomeViewModel(
    private val repository: SubscriptionRepository,
    private val vpnController: VpnController
) : ViewModel() {
    private val busySubscriptionIds = MutableStateFlow<Set<String>>(emptySet())
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

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

    val uiState: StateFlow<HomeUiState> = combine(baseState, message) { state, currentMessage ->
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
            val source = if (value.trim().startsWith("vless://", ignoreCase = true)) {
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
            repository.refreshAll()
                .mapNotNull(Result<Unit>::exceptionOrNull)
                .firstOrNull()
                ?.let { message.value = it.message ?: "Refresh failed" }
        }
    }

    fun delete(subscriptionId: String) {
        viewModelScope.launch {
            val group = uiState.value.groups.firstOrNull { it.subscription.id == subscriptionId }
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
}
