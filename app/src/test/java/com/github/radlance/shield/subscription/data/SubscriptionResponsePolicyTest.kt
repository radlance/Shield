package com.github.radlance.shield.subscription.data

import com.github.radlance.shield.subscription.domain.SubscriptionDeviceLimitException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionResponsePolicyTest {
    @Test
    fun returnsBodyWhenDeviceHeadersAllowIt() {
        val response = DownloadedSubscription(
            body = "subscription",
            contentType = "text/plain",
            headers = mapOf("x-hwid-active" to "true")
        )

        assertEquals("subscription", response.validatedBody())
    }

    @Test
    fun rejectsReachedDeviceLimit() {
        val response = DownloadedSubscription(
            body = "ignored",
            contentType = "text/plain",
            headers = mapOf("x-hwid-max-devices-reached" to "TRUE")
        )

        val failure = runCatching { response.validatedBody() }

        assertTrue(failure.exceptionOrNull() is SubscriptionDeviceLimitException)
    }

    @Test
    fun rejectsUnacceptedDeviceIdentifier() {
        val response = DownloadedSubscription(
            body = "ignored",
            contentType = "text/plain",
            headers = mapOf("x-hwid-not-supported" to "true")
        )

        val failure = runCatching { response.validatedBody() }

        assertTrue(failure.exceptionOrNull() is SubscriptionDeviceLimitException)
    }
}
