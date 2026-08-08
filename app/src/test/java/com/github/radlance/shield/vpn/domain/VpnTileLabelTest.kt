package com.github.radlance.shield.vpn.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnTileLabelTest {
    @Test
    fun usesProfileNameWhileConnecting() {
        assertEquals(
            "Frankfurt 01",
            VpnTileLabel.forState(VpnConnectionState.Connecting("Frankfurt 01"), "Shield VPN")
        )
    }

    @Test
    fun usesConnectedProfileName() {
        assertEquals(
            "Tokyo",
            VpnTileLabel.forState(
                VpnConnectionState.Connected("profile", "Tokyo", 0L),
                "Shield VPN"
            )
        )
    }

    @Test
    fun fallsBackWhenNotConnected() {
        assertEquals(
            "Shield VPN",
            VpnTileLabel.forState(VpnConnectionState.Disconnected, "Shield VPN")
        )
    }

    @Test
    fun normalizesAndBoundsLongNames() {
        assertEquals(
            "A very long server name…",
            VpnTileLabel.forState(
                VpnConnectionState.Connecting("  A   very   long   server name in Germany   "),
                "Shield VPN"
            )
        )
    }
}
