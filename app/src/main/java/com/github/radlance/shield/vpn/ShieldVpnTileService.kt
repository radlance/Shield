package com.github.radlance.shield.vpn

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.github.radlance.shield.R
import com.github.radlance.shield.core.MainActivity
import com.github.radlance.shield.subscription.domain.SubscriptionAccessStatus
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.subscription.domain.SubscriptionGroup
import com.github.radlance.shield.subscription.domain.accessStatus
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import com.github.radlance.shield.vpn.domain.VpnController
import com.github.radlance.shield.vpn.domain.VpnTileLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ShieldVpnTileService : TileService() {
    private val vpnController by inject<VpnController>()
    private val repository by inject<SubscriptionRepository>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            combine(
                vpnController.state,
                repository.groups,
                repository.selectedProfileId
            ) { state, groups, selectedProfileId ->
                state to hasAvailableSubscription(selectedProfileId, groups)
            }.collect { (state, canActivate) ->
                updateTile(state, canActivate)
            }
        }
    }

    override fun onStopListening() {
        stateJob?.cancel()
        stateJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (vpnController.state.value) {
            is VpnConnectionState.Connected,
            is VpnConnectionState.Reconnecting -> vpnController.disconnect()
            is VpnConnectionState.Connecting,
            is VpnConnectionState.Disconnecting -> Unit
            else -> connectSelectedProfile()
        }
    }

    override fun onDestroy() {
        stateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun connectSelectedProfile() {
        serviceScope.launch {
            val profileId = repository.selectedProfileId.first()
            val profile = profileId?.let { repository.getProfile(it) }
            val groups = repository.groups.first()
            if (profile == null) {
                openMainActivity()
                return@launch
            }
            if (!hasAvailableSubscription(profileId, groups)) {
                updateTile(vpnController.state.value, canActivate = false)
                return@launch
            }
            if (VpnService.prepare(this@ShieldVpnTileService) != null ||
                !hasNotificationPermission()
            ) {
                openMainActivity()
                return@launch
            }
            vpnController.connect(profile.id)
        }
    }

    private fun openMainActivity() {
        unlockAndRun {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                @SuppressLint("StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasAvailableSubscription(
        profileId: String?,
        groups: List<SubscriptionGroup>
    ): Boolean {
        if (profileId == null) return false
        val subscription = groups
            .firstOrNull { group -> group.profiles.any { it.id == profileId } }
            ?.subscription
            ?: return false
        return subscription.metadata.accessStatus(System.currentTimeMillis() / 1_000) ==
            SubscriptionAccessStatus.AVAILABLE
    }

    private fun updateTile(state: VpnConnectionState, canActivate: Boolean) {
        val tile = qsTile ?: return
        val active = state is VpnConnectionState.Connected ||
            state is VpnConnectionState.Reconnecting
        tile.state = when {
            active -> Tile.STATE_ACTIVE
            state is VpnConnectionState.Connecting ||
                state is VpnConnectionState.Disconnecting -> Tile.STATE_UNAVAILABLE
            !canActivate -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = VpnTileLabel.forState(
            state = state,
            fallback = getString(R.string.quick_settings_vpn)
        )
        tile.icon = Icon.createWithResource(this, R.drawable.shield_24px)
        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, ShieldVpnTileService::class.java)
            )
        }
    }
}
