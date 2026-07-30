package com.github.radlance.shield.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.radlance.shield.vpn.domain.VpnController
import com.github.radlance.shield.vpn.routing.RoutingSettings
import com.github.radlance.shield.vpn.routing.RoutingSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoutingSettingsViewModel(
    private val repository: RoutingSettingsRepository,
    private val vpnController: VpnController
) : ViewModel() {
    private val updateMutex = Mutex()

    val uiState: StateFlow<RoutingSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutingSettings()
    )

    fun setSmartRussianRouting(enabled: Boolean) {
        mutate { settings -> settings.copy(smartRussianRouting = enabled) }
    }

    fun setForceDirectDomains(domains: Set<String>) {
        mutate { settings -> settings.copy(forceDirectDomains = domains) }
    }

    fun setForceProxyDomains(domains: Set<String>) {
        mutate { settings -> settings.copy(forceProxyDomains = domains) }
    }

    private fun mutate(transform: (RoutingSettings) -> RoutingSettings) {
        viewModelScope.launch {
            updateMutex.withLock {
                repository.update(transform(repository.settings.first()))
            }
            vpnController.reload()
        }
    }
}
