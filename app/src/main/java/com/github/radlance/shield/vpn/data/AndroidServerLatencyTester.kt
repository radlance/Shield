package com.github.radlance.shield.vpn.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.github.radlance.shield.subscription.domain.VlessProfile
import com.github.radlance.shield.vpn.domain.ServerLatencyTester
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import com.github.radlance.shield.vpn.domain.VpnController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetSocketAddress
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

class AndroidServerLatencyTester(
    context: Context,
    private val physicalNetworkMonitor: PhysicalNetworkMonitor,
    private val vpnController: VpnController
) : ServerLatencyTester {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    override suspend fun measure(profile: VlessProfile): Long? {
        val network = probeNetwork()
            ?: return null
        val deadline = SystemClock.elapsedRealtime() + PROBE_TIMEOUT_MILLIS
        return withTimeoutOrNull(PROBE_TIMEOUT_MILLIS.milliseconds) {
            val addresses = runInterruptible(Dispatchers.IO) {
                network.getAllByName(profile.server)
                    .sortedBy { it !is Inet4Address }
            }
            withContext(Dispatchers.IO) {
                for (address in addresses) {
                    currentCoroutineContext().ensureActive()
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0) return@withContext null
                    val latency = connect(
                        network = network,
                        address = InetSocketAddress(address, profile.port),
                        timeoutMillis = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    )
                    if (latency != null) return@withContext latency
                }
                null
            }
        }
    }

    private suspend fun probeNetwork(): Network? {
        if (vpnController.state.value.canUseDefaultNetworkForLatency()) {
            connectivity.activeNetwork
                ?.takeIf(::hasInternetCapability)
                ?.let { return it }
        }
        return physicalNetworkMonitor.awaitNetwork(NETWORK_TIMEOUT_MILLIS)
    }

    private fun hasInternetCapability(network: Network): Boolean =
        connectivity.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private suspend fun connect(
        network: Network,
        address: InetSocketAddress,
        timeoutMillis: Int
    ): Long? {
        val socket = withContext(Dispatchers.IO) {
            network.socketFactory.createSocket()
        }
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { error ->
            if (error is CancellationException) runCatching(socket::close)
        }
        return try {
            val startedAt = SystemClock.elapsedRealtimeNanos()
            withContext(Dispatchers.IO) {
                socket.connect(address, timeoutMillis)
            }
            ceil((SystemClock.elapsedRealtimeNanos() - startedAt) / NANOS_PER_MILLI)
                .toLong()
                .coerceAtLeast(1L)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } finally {
            cancellationHandle.dispose()
            runCatching(socket::close)
        }
    }

    private companion object {
        const val NETWORK_TIMEOUT_MILLIS = 5_000L
        const val PROBE_TIMEOUT_MILLIS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}

private fun VpnConnectionState.canUseDefaultNetworkForLatency(): Boolean = when (this) {
    VpnConnectionState.Disconnected,
    VpnConnectionState.PermissionRequired,
    is VpnConnectionState.Error -> true
    is VpnConnectionState.Connecting,
    is VpnConnectionState.Connected,
    is VpnConnectionState.Reconnecting,
    VpnConnectionState.Disconnecting -> false
}
