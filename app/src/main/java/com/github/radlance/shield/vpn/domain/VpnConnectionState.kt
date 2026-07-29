package com.github.radlance.shield.vpn.domain

sealed interface VpnConnectionState {
    data object Disconnected : VpnConnectionState
    data object PermissionRequired : VpnConnectionState
    data class Connecting(val profileName: String) : VpnConnectionState
    data class Connected(
        val profileId: String,
        val profileName: String,
        val connectedAtElapsedRealtime: Long
    ) : VpnConnectionState
    data class Reconnecting(val profileName: String) : VpnConnectionState
    data object Disconnecting : VpnConnectionState
    data class Error(val message: String) : VpnConnectionState
}
