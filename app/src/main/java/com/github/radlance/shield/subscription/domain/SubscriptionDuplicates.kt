package com.github.radlance.shield.subscription.domain

import java.net.URI

internal fun isExistingSubscription(
    subscriptions: List<Subscription>,
    profiles: List<VlessProfile>,
    source: SubscriptionSource,
    importedProfiles: List<VlessProfile>
): Boolean = when (source) {
    is SubscriptionSource.Remote -> subscriptions.any { subscription ->
        subscription.sourceUrl?.let { existingUrl ->
            subscriptionUrlKey(existingUrl) == subscriptionUrlKey(source.url)
        } == true
    }

    is SubscriptionSource.Direct -> {
        val existingProfileIds = profiles.mapTo(hashSetOf(), VlessProfile::id)
        importedProfiles.any { it.id in existingProfileIds }
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
