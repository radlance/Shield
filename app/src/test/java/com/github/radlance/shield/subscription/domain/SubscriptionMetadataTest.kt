package com.github.radlance.shield.subscription.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionMetadataTest {
    @Test
    fun detectsExpiredSubscription() {
        val metadata = SubscriptionMetadata(expiresAtEpochSeconds = 100)

        assertEquals(
            SubscriptionAccessStatus.EXPIRED,
            metadata.accessStatus(nowEpochSeconds = 100)
        )
    }

    @Test
    fun detectsExhaustedPositiveTrafficLimit() {
        val metadata = SubscriptionMetadata(
            uploadBytes = 400,
            downloadBytes = 600,
            totalBytes = 1_000
        )

        assertEquals(
            SubscriptionAccessStatus.TRAFFIC_EXHAUSTED,
            metadata.accessStatus(nowEpochSeconds = 0)
        )
    }

    @Test
    fun treatsMissingOrZeroTrafficLimitAsAvailable() {
        val withoutLimit = SubscriptionMetadata(uploadBytes = 1_000)
        val zeroLimit = SubscriptionMetadata(uploadBytes = 1_000, totalBytes = 0)

        assertEquals(
            SubscriptionAccessStatus.AVAILABLE,
            withoutLimit.accessStatus(nowEpochSeconds = 0)
        )
        assertEquals(
            SubscriptionAccessStatus.AVAILABLE,
            zeroLimit.accessStatus(nowEpochSeconds = 0)
        )
    }

    @Test
    fun usedTrafficDoesNotOverflow() {
        val metadata = SubscriptionMetadata(
            uploadBytes = Long.MAX_VALUE,
            downloadBytes = 1
        )

        assertEquals(Long.MAX_VALUE, metadata.usedBytes)
    }

    @Test
    fun oldStoredSubscriptionGetsEmptyMetadata() {
        val subscription = Json.decodeFromString<Subscription>(
            """
                {
                    "id": "subscription",
                    "name": "Shield",
                    "createdAtEpochMillis": 1
                }
            """.trimIndent()
        )

        assertEquals(SubscriptionMetadata(), subscription.metadata)
    }
}
