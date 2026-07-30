package com.github.radlance.shield.subscription.domain

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val sourceUrl: String? = null,
    val createdAtEpochMillis: Long,
    val lastUpdatedAtEpochMillis: Long? = null,
    val lastError: String? = null
)

@Serializable
data class VlessProfile(
    val id: String,
    val subscriptionId: String,
    val name: String,
    val server: String,
    val port: Int,
    val uuid: String,
    val transport: VlessTransport = VlessTransport.TCP,
    val security: VlessSecurity = VlessSecurity.NONE,
    val flow: String? = null,
    val serverName: String? = null,
    val alpn: List<String> = emptyList(),
    val fingerprint: String? = null,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,
    val path: String? = null,
    val host: String? = null,
    val grpcServiceName: String? = null,
    val packetEncoding: String? = null
)

@Serializable
enum class VlessTransport {
    TCP,
    WEBSOCKET,
    GRPC
}

@Serializable
enum class VlessSecurity {
    NONE,
    TLS,
    REALITY
}

data class SubscriptionGroup(
    val subscription: Subscription,
    val profiles: List<VlessProfile>
)

data class ImportResult(
    val profiles: List<VlessProfile>,
    val rejectedEntries: Int,
    val unsupportedTransports: Set<String> = emptySet()
)

sealed interface SubscriptionSource {
    data class Remote(val url: String) : SubscriptionSource
    data class Direct(val link: String) : SubscriptionSource
}
