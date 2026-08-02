package com.github.radlance.shield.vpn.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class PhysicalNetworkMonitor(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private val networks = MutableStateFlow(connectivity.findPhysicalNetworks())

    init {
        connectivity.registerNetworkCallback(
            PHYSICAL_NETWORK_REQUEST,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networks.update { current -> current + (network to 0) }
                }

                override fun onLost(network: Network) {
                    networks.update { current -> current - network }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    networks.update { current ->
                        if (capabilities.isPhysicalNetwork()) {
                            current + (network to capabilities.physicalNetworkScore())
                        } else {
                            current - network
                        }
                    }
                }
            }
        )
    }

    suspend fun awaitNetwork(timeoutMillis: Long): Network? {
        bestNetwork(networks.value)?.let { return it }
        if (timeoutMillis <= 0) return null

        return withTimeoutOrNull(timeoutMillis.milliseconds) {
            networks.mapNotNull(::bestNetwork).first()
        }
    }
}

@Suppress("DEPRECATION")
private fun ConnectivityManager.findPhysicalNetworks(): Map<Network, Int> = allNetworks
    .mapNotNull { network ->
        val capabilities = getNetworkCapabilities(network) ?: return@mapNotNull null
        if (!capabilities.isPhysicalNetwork()) return@mapNotNull null
        network to capabilities.physicalNetworkScore()
    }
    .toMap()

private fun bestNetwork(networks: Map<Network, Int>): Network? =
    networks.maxByOrNull { it.value }?.key

private fun NetworkCapabilities.isPhysicalNetwork(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
        !hasTransport(NetworkCapabilities.TRANSPORT_VPN)

private fun NetworkCapabilities.physicalNetworkScore(): Int =
    (if (hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0) +
        when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 30
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 20
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 10
            else -> 0
        }

private val PHYSICAL_NETWORK_REQUEST = NetworkRequest.Builder()
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    .build()
