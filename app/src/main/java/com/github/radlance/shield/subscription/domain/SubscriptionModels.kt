package com.github.radlance.shield.subscription.domain

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val sourceUrl: String? = null,
    val createdAtEpochMillis: Long,
    val lastUpdatedAtEpochMillis: Long? = null,
    val lastError: String? = null,
    val pinOrder: Long? = null,
    val metadata: SubscriptionMetadata = SubscriptionMetadata()
)

@Serializable
data class SubscriptionMetadata(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
    val announcement: String? = null,
    val supportUrl: String? = null,
    val webPageUrl: String? = null,
    val updateIntervalHours: Int? = null
) {
    val usedBytes: Long?
        get() {
            val values = listOfNotNull(uploadBytes, downloadBytes)
            if (values.isEmpty()) return null
            return values.fold(0L) { total, value ->
                if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
            }
        }
}

enum class SubscriptionAccessStatus {
    AVAILABLE,
    EXPIRED,
    TRAFFIC_EXHAUSTED
}

fun SubscriptionMetadata.accessStatus(nowEpochSeconds: Long): SubscriptionAccessStatus {
    if (expiresAtEpochSeconds?.let { nowEpochSeconds >= it } == true) {
        return SubscriptionAccessStatus.EXPIRED
    }
    val used = usedBytes
    if (totalBytes?.let { it > 0 && used != null && used >= it } == true) {
        return SubscriptionAccessStatus.TRAFFIC_EXHAUSTED
    }
    return SubscriptionAccessStatus.AVAILABLE
}

@Serializable
data class ProxyProfile(
    val id: String,
    val subscriptionId: String,
    val name: String,
    val server: String,
    val port: Int,
    val uuid: String = "",
    val protocol: ProxyProtocol = ProxyProtocol.VLESS,
    val outboundJson: String? = null,
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

typealias VlessProfile = ProxyProfile

@Serializable
enum class ProxyProtocol {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
    HYSTERIA2,
    TUIC
}

fun String.isSupportedProxyLink(): Boolean =
    substringBefore(':').lowercase() in setOf(
        "vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "tuic"
    ) && substringAfter(':', "").startsWith("//")

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
    val profiles: List<ProxyProfile>
)

data class ImportResult(
    val profiles: List<ProxyProfile>,
    val rejectedEntries: Int,
    val unsupportedTransports: Set<String> = emptySet()
)

sealed interface SubscriptionSource {
    data class Remote(val url: String) : SubscriptionSource
    data class Direct(val link: String) : SubscriptionSource
}
