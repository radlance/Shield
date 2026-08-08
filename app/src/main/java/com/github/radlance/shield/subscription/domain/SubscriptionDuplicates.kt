package com.github.radlance.shield.subscription.domain

import java.net.URI

internal fun isExistingSubscription(
    subscriptions: List<Subscription>,
    profiles: List<ProxyProfile>,
    source: SubscriptionSource,
    importedProfiles: List<ProxyProfile>
): Boolean = when (source) {
    is SubscriptionSource.Remote -> subscriptions.any { subscription ->
        subscription.sourceUrl?.let { existingUrl ->
            subscriptionUrlKey(existingUrl) == subscriptionUrlKey(source.url)
        } == true
    }

    is SubscriptionSource.Direct -> {
        importedProfiles.any { imported ->
            profiles.any { existing -> existing.sameEndpointIdentity(imported) }
        }
    }
}

private fun ProxyProfile.sameEndpointIdentity(other: ProxyProfile): Boolean {
    if (id == other.id) return true
    if (
        protocol != other.protocol || server.lowercase() != other.server.lowercase() ||
        port != other.port
    ) return false
    return when (protocol) {
        ProxyProtocol.VLESS -> uuid.equals(other.uuid, ignoreCase = true) &&
            transport == other.transport && security == other.security && flow == other.flow &&
            serverName == other.serverName && path == other.path && host == other.host &&
            grpcServiceName == other.grpcServiceName
        ProxyProtocol.VMESS,
        ProxyProtocol.TUIC -> uuid.isNotBlank() && uuid.equals(other.uuid, ignoreCase = true)
        else -> false
    }
}

private fun subscriptionUrlKey(value: String): SubscriptionUrlKey {
    val uri = URI(value.trim()).normalize()
    return SubscriptionUrlKey(
        scheme = uri.scheme?.lowercase(),
        userInfo = uri.rawUserInfo,
        host = uri.host?.lowercase(),
        port = uri.port.takeIf { it != -1 && it != 443 },
        path = uri.rawPath.orEmpty().ifEmpty { "/" },
        query = uri.rawQuery
    )
}

private data class SubscriptionUrlKey(
    val scheme: String?,
    val userInfo: String?,
    val host: String?,
    val port: Int?,
    val path: String,
    val query: String?
)
