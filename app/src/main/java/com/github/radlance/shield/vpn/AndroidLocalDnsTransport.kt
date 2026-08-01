package com.github.radlance.shield.vpn

import android.content.Context
import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.system.ErrnoException
import androidx.annotation.RequiresApi
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidLocalDnsTransport(
    context: Context,
    private val networkProvider: () -> Network?
) : LocalDNSTransport {
    private val applicationContext = context.applicationContext
    private var resolverInstance: Any? = null

    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun exchange(context: ExchangeContext, message: ByteArray) {
        val network = networkProvider()
        if (network == null) {
            context.errorCode(RCODE_SERVFAIL)
            return
        }
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                context.onCancel {
                    signal.cancel()
                    continuation.cancel()
                }
                resolver().rawQuery(
                    network,
                    message,
                    DnsResolver.FLAG_EMPTY,
                    Dispatchers.IO.asExecutor(),
                    signal,
                    object : DnsResolver.Callback<ByteArray> {
                        override fun onAnswer(answer: ByteArray, rcode: Int) {
                            if (rcode == 0 && answer.isNotEmpty()) {
                                context.rawSuccess(answer)
                            } else {
                                context.errorCode(if (rcode == 0) RCODE_SERVFAIL else rcode)
                            }
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            val cause = error.cause
                            if (cause is ErrnoException) {
                                context.errnoCode(cause.errno)
                                if (continuation.isActive) continuation.resume(Unit)
                            } else {
                                context.errorCode(RCODE_SERVFAIL)
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun lookup(context: ExchangeContext, networkName: String, domain: String) {
        val network = networkProvider()
        if (network == null) {
            context.errorCode(RCODE_SERVFAIL)
            return
        }
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
                            val addresses = answer.mapNotNull(InetAddress::getHostAddress)
                            if (addresses.isEmpty()) {
                                context.errorCode(RCODE_SERVFAIL)
                            } else {
                                context.success(addresses.joinToString("\n"))
                            }
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
                        } else {
                            context.errorCode(RCODE_SERVFAIL)
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                }
                val type = when {
                    networkName.endsWith("4") -> DnsResolver.TYPE_A
                    networkName.endsWith("6") -> DnsResolver.TYPE_AAAA
                    else -> null
                }
                if (type == null) {
                    resolver().query(
                        network,
                        domain,
                        DnsResolver.FLAG_EMPTY,
                        Dispatchers.IO.asExecutor(),
                        signal,
                        callback
                    )
                } else {
                    resolver().query(
                        network,
                        domain,
                        type,
                        DnsResolver.FLAG_EMPTY,
                        Dispatchers.IO.asExecutor(),
                        signal,
                        callback
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun resolver(): DnsResolver {
        val current = resolverInstance
        if (current is DnsResolver) return current
        return createDnsResolver(applicationContext).also { resolverInstance = it }
    }

    private companion object {
        const val RCODE_SERVFAIL = 2
        const val RCODE_NXDOMAIN = 3

        @RequiresApi(Build.VERSION_CODES.Q)
        fun createDnsResolver(context: Context): DnsResolver =
            if (Build.VERSION.SDK_INT >= 37) {
                DnsResolver(context, Looper.getMainLooper())
            } else {
                DnsResolver::class.java
                    .getMethod("getInstance")
                    .invoke(null) as DnsResolver
            }
    }
}
