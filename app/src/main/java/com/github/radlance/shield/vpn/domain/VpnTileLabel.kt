package com.github.radlance.shield.vpn.domain

object VpnTileLabel {
    private const val MAX_SERVER_NAME_LENGTH = 24
    private val whitespace = Regex("\\s+")

    fun forState(state: VpnConnectionState, fallback: String): String {
        val serverName = when (state) {
            is VpnConnectionState.Connecting -> state.profileName
            is VpnConnectionState.Connected -> state.profileName
            is VpnConnectionState.Reconnecting -> state.profileName
            VpnConnectionState.Disconnected,
            VpnConnectionState.PermissionRequired,
            VpnConnectionState.Disconnecting,
            is VpnConnectionState.Error -> null
        }
        return format(serverName, fallback)
    }

    private fun format(serverName: String?, fallback: String): String {
        val normalized = serverName
            ?.trim()
            ?.replace(whitespace, " ")
            ?.takeIf(String::isNotEmpty)
            ?: return fallback
        if (normalized.length <= MAX_SERVER_NAME_LENGTH) return normalized
        return normalized.take(MAX_SERVER_NAME_LENGTH - 1).trimEnd() + "…"
    }
}
