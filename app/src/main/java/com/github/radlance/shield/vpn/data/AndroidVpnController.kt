package com.github.radlance.shield.vpn.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.github.radlance.shield.vpn.ShieldVpnService
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import com.github.radlance.shield.vpn.domain.VpnController
import kotlinx.coroutines.flow.StateFlow

class AndroidVpnController(
    private val context: Context
) : VpnController {
    override val state: StateFlow<VpnConnectionState> = ShieldVpnService.connectionState

    override fun connect(profileId: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ShieldVpnService::class.java)
                .setAction(ShieldVpnService.ACTION_CONNECT)
                .putExtra(ShieldVpnService.EXTRA_PROFILE_ID, profileId)
        )
    }

    override fun switchProfile(profileId: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ShieldVpnService::class.java)
                .setAction(ShieldVpnService.ACTION_SWITCH_PROFILE)
                .putExtra(ShieldVpnService.EXTRA_PROFILE_ID, profileId)
        )
    }

    override fun disconnect() {
        context.startService(
            Intent(context, ShieldVpnService::class.java)
                .setAction(ShieldVpnService.ACTION_DISCONNECT)
        )
    }

    override fun reload() {
        if (
            state.value !is VpnConnectionState.Connected &&
            state.value !is VpnConnectionState.Reconnecting
        ) return
        context.startService(
            Intent(context, ShieldVpnService::class.java)
                .setAction(ShieldVpnService.ACTION_RELOAD)
        )
    }
}
