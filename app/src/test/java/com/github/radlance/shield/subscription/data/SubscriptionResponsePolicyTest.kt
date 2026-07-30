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

    @Test
    fun parsesSubscriptionMetadataFromHeaders() {
        val response = DownloadedSubscription(
            body = "vless://profile",
            contentType = "text/plain",
            headers = mapOf(
                "Subscription-Userinfo" to
                    "upload=1024; download=2048; total=8192; expire=2000000000",
                "Announce" to "base64:U2VydmljZSBtZXNzYWdl",
                "Support-Url" to "tg://resolve?domain=shield",
                "Profile-Web-Page-Url" to "https://example.com/account",
                "Profile-Update-Interval" to "6"
            )
        )

        val metadata = response.metadata()

        assertEquals(1024L, metadata.uploadBytes)
        assertEquals(2048L, metadata.downloadBytes)
        assertEquals(3072L, metadata.usedBytes)
        assertEquals(8192L, metadata.totalBytes)
        assertEquals(2000000000L, metadata.expiresAtEpochSeconds)
        assertEquals("Service message", metadata.announcement)
        assertEquals("tg://resolve?domain=shield", metadata.supportUrl)
        assertEquals("https://example.com/account", metadata.webPageUrl)
        assertEquals(6, metadata.updateIntervalHours)
    }

    @Test
    fun readsMetadataFromBodyAndIgnoresInvalidFields() {
        val response = DownloadedSubscription(
            body = """
                #subscription-userinfo: upload=-1; download=512; total=invalid
                //announce: Maintenance tonight
                #support-url: javascript:alert(1)
                #profile-update-interval: 0
                vless://profile
            """.trimIndent(),
            contentType = "text/plain",
            headers = emptyMap()
        )

        val metadata = response.metadata()

        assertEquals(null, metadata.uploadBytes)
        assertEquals(512L, metadata.downloadBytes)
        assertEquals(null, metadata.totalBytes)
        assertEquals("Maintenance tonight", metadata.announcement)
        assertEquals(null, metadata.supportUrl)
        assertEquals(null, metadata.updateIntervalHours)
    }
}
