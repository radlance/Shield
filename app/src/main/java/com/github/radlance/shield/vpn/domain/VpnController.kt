package com.github.radlance.shield.vpn.domain

import kotlinx.coroutines.flow.StateFlow

interface VpnController {
    val state: StateFlow<VpnConnectionState>

    fun connect(profileId: String)
    fun switchProfile(profileId: String)
    fun disconnect()
    fun reload()
}
