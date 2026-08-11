package com.github.radlance.shield.subscription.domain

import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    val groups: Flow<List<SubscriptionGroup>>
    val selectedProfileId: Flow<String?>

    suspend fun import(name: String, source: SubscriptionSource): Result<Subscription>
    suspend fun refresh(subscriptionId: String): Result<Unit>
    suspend fun refreshAll(): List<Result<Unit>>
    suspend fun delete(subscriptionId: String)
    suspend fun setPinned(subscriptionId: String, pinned: Boolean)
    suspend fun selectProfile(profileId: String)
    suspend fun getProfile(profileId: String): ProxyProfile?
}
