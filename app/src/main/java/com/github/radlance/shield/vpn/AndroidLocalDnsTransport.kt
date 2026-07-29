package com.github.radlance.shield.vpn

import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import androidx.annotation.RequiresApi
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidLocalDnsTransport(
    private val networkProvider: () -> Network?
) : LocalDNSTransport {
    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun exchange(context: ExchangeContext, message: ByteArray) {
        val network = networkProvider() ?: error("Underlying network is unavailable")
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                context.onCancel {
                    signal.cancel()
                    continuation.cancel()
                }
                DnsResolver.getInstance().rawQuery(
                    network,
                    message,
                    DnsResolver.FLAG_NO_RETRY,
                    Dispatchers.IO.asExecutor(),
                    signal,
                    object : DnsResolver.Callback<ByteArray> {
                        override fun onAnswer(answer: ByteArray, rcode: Int) {
                            if (rcode == 0) context.rawSuccess(answer) else context.errorCode(rcode)
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            val cause = error.cause
                            if (cause is ErrnoException) {
                                context.errnoCode(cause.errno)
                                if (continuation.isActive) continuation.resume(Unit)
                            } else if (continuation.isActive) {
                                continuation.resumeWithException(error)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun lookup(context: ExchangeContext, networkName: String, domain: String) {
        val network = networkProvider() ?: error("Underlying network is unavailable")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val addresses = try {
                network.getAllByName(domain)
            } catch (_: UnknownHostException) {
                context.errorCode(RCODE_NXDOMAIN)
                return
            }
            context.success(addresses.mapNotNull(InetAddress::getHostAddress).joinToString("\n"))
            return
        }
        lookupModern(context, network, networkName, domain)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun lookupModern(
        context: ExchangeContext,
        network: Network,
        networkName: String,
        domain: String
    ) {
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                context.onCancel {
                    signal.cancel()
                    continuation.cancel()
                }
                val callback = object : DnsResolver.Callback<Collection<InetAddress>> {
                    override fun onAnswer(answer: Collection<InetAddress>, rcode: Int) {
                        if (rcode == 0) {
                            context.success(answer.mapNotNull(InetAddress::getHostAddress).joinToString("\n"))
                        } else {
                            context.errorCode(rcode)
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(error: DnsResolver.DnsException) {
                        val cause = error.cause
                        if (cause is ErrnoException) {
                            context.errnoCode(cause.errno)
                            if (continuation.isActive) continuation.resume(Unit)
                        } else if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }
                val type = when {
                    networkName.endsWith("4") -> DnsResolver.TYPE_A
                    networkName.endsWith("6") -> DnsResolver.TYPE_AAAA
                    else -> null
                }
                if (type == null) {
                    DnsResolver.getInstance().query(
                        network,
                        domain,
                        DnsResolver.FLAG_NO_RETRY,
                        Dispatchers.IO.asExecutor(),
                        signal,
                        callback
                    )
                } else {
                    DnsResolver.getInstance().query(
                        network,
                        domain,
                        type,
                        DnsResolver.FLAG_NO_RETRY,
                        Dispatchers.IO.asExecutor(),
                        signal,
                        callback
                    )
                }
            }
        }
    }

    private companion object {
        const val RCODE_NXDOMAIN = 3
    }
}
