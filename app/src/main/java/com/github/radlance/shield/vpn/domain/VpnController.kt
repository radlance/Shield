package com.github.radlance.shield.vpn.domain

import kotlinx.coroutines.flow.StateFlow

interface VpnController {
    val state: StateFlow<VpnConnectionState>

    fun connect(profileId: String)
    fun disconnect()
}
