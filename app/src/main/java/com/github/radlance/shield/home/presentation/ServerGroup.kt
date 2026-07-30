package com.github.radlance.shield.home.presentation

import com.github.radlance.shield.subscription.domain.SubscriptionAccessStatus
import com.github.radlance.shield.subscription.domain.SubscriptionMetadata

data class ServerGroup(
    val id: String,
    val title: String,
    val items: List<ServerItem>,
    val metadata: SubscriptionMetadata = SubscriptionMetadata(),
    val accessStatus: SubscriptionAccessStatus = SubscriptionAccessStatus.AVAILABLE,
    val onRefresh: (() -> Unit)? = null,
    val onPing: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val isRefreshing: Boolean = false,
    val isPinging: Boolean = false,
    val error: String? = null
) {
    val isUnavailable: Boolean
        get() = accessStatus != SubscriptionAccessStatus.AVAILABLE
}
