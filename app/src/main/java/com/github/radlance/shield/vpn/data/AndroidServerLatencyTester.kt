package com.github.radlance.shield.vpn.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.github.radlance.shield.subscription.domain.VlessProfile
import com.github.radlance.shield.vpn.domain.ServerLatencyTester
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
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

class AndroidServerLatencyTester(
    context: Context
) : ServerLatencyTester {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override suspend fun measure(profile: VlessProfile): Long? {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            val network = physicalNetwork() ?: return@withTimeoutOrNull null
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

    private suspend fun connect(
        network: Network,
        address: InetSocketAddress,
        timeoutMillis: Int
    ): Long? {
        val socket = network.socketFactory.createSocket()
        val cancellationHandle = coroutineContext.job.invokeOnCompletion { error ->
            if (error is CancellationException) runCatching(socket::close)
        }
        return try {
            val startedAt = SystemClock.elapsedRealtimeNanos()
            socket.connect(address, timeoutMillis)
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

    @Suppress("DEPRECATION")
    private fun physicalNetwork(): Network? = connectivity.allNetworks
        .mapNotNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network)
                ?: return@mapNotNull null
            if (
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return@mapNotNull null
            }
            network to networkScore(capabilities)
        }
        .maxByOrNull { it.second }
        ?.first

    private fun networkScore(capabilities: NetworkCapabilities): Int {
        val validatedScore =
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100
            else 0
        val transportScore = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 30
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 20
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 10
            else -> 0
        }
        return validatedScore + transportScore
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
