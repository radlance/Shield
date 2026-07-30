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

    @Test
    fun decodesProfileTitleFromHeader() {
        val response = DownloadedSubscription(
            body = "subscription",
            contentType = "text/plain",
            headers = mapOf(
                "Profile-Title" to "base64:SSDinaTvuI8gSGlkZGlmeQ=="
            )
        )

        assertEquals("I ❤️ Hiddify", response.profileTitle())
    }

    @Test
    fun readsPlainProfileTitleFromBody() {
        val response = DownloadedSubscription(
            body = """
                #profile-title: Shield VPN
                vless://profile
            """.trimIndent(),
            contentType = "text/plain",
            headers = emptyMap()
        )

        assertEquals("Shield VPN", response.profileTitle())
    }
}
