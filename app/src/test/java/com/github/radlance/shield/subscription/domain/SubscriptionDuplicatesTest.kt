package com.github.radlance.shield.subscription.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDuplicatesTest {
    @Test
    fun detectsEquivalentRemoteSubscriptionUrl() {
        val subscription = subscription("HTTPS://Example.com:443/a/../feed?token=abc")

        assertTrue(
            isExistingSubscription(
                subscriptions = listOf(subscription),
                profiles = emptyList(),
                source = SubscriptionSource.Remote("https://example.com/feed?token=abc"),
                importedProfiles = emptyList()
            )
        )
    }

    @Test
    fun allowsDifferentRemoteSubscriptionUrl() {
        val subscription = subscription("https://example.com/feed?token=abc")

        assertFalse(
            isExistingSubscription(
                subscriptions = listOf(subscription),
                profiles = emptyList(),
                source = SubscriptionSource.Remote("https://example.com/feed?token=other"),
                importedProfiles = emptyList()
            )
        )
    }

    @Test
    fun detectsDirectSubscriptionByProfileIdentity() {
        val existing = profile(id = "profile", subscriptionId = "existing")
        val imported = profile(id = "profile", subscriptionId = "new")

        assertTrue(
            isExistingSubscription(
                subscriptions = listOf(subscription(sourceUrl = null)),
                profiles = listOf(existing),
                source = SubscriptionSource.Direct("vless://link"),
                importedProfiles = listOf(imported)
            )
        )
    }

    private fun subscription(sourceUrl: String?) = Subscription(
        id = "subscription",
        name = "Subscription",
        sourceUrl = sourceUrl,
        createdAtEpochMillis = 0
    )

    private fun profile(id: String, subscriptionId: String) = VlessProfile(
        id = id,
        subscriptionId = subscriptionId,
        name = "Server",
        server = "example.com",
        port = 443,
        uuid = "00000000-0000-0000-0000-000000000000"
    )
}
