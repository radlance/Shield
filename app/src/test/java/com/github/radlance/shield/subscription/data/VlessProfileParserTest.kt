package com.github.radlance.shield.subscription.data

import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessProfileParserTest {
    private val parser = VlessProfileParser()

    @Test
    fun parsesWebSocketTlsLink() {
        val profile = parser.parseVless(
            "vless://123e4567-e89b-42d3-a456-426614174000@example.com:443" +
                "?type=ws&security=tls&sni=cdn.example.com&fp=chrome&path=%2Fvpn&host=edge.example.com#Amsterdam",
            "subscription"
        )

        assertEquals("Amsterdam", profile.name)
        assertEquals(VlessTransport.WEBSOCKET, profile.transport)
        assertEquals(VlessSecurity.TLS, profile.security)
        assertEquals("/vpn", profile.path)
        assertEquals("edge.example.com", profile.host)
        assertEquals("cdn.example.com", profile.serverName)
    }

    @Test
    fun parsesBase64SubscriptionAndRejectsInvalidEntries() {
        val valid = "vless://123e4567-e89b-42d3-a456-426614174000@example.com:443" +
            "?type=grpc&security=reality&sni=example.org&pbk=public-key&sid=abcd&serviceName=tunnel#Node"
        val encoded = Base64.getEncoder().encodeToString("$valid\nnot-a-profile".toByteArray())

        val result = parser.parseSubscription(encoded, "subscription")

        assertEquals(1, result.profiles.size)
        assertEquals(1, result.rejectedEntries)
        assertEquals(VlessTransport.GRPC, result.profiles.single().transport)
        assertEquals(VlessSecurity.REALITY, result.profiles.single().security)
    }

    @Test
    fun requiresRealityKeyAndServerName() {
        val failure = runCatching {
            parser.parseVless(
                "vless://123e4567-e89b-42d3-a456-426614174000@example.com:443?security=reality",
                "subscription"
            )
        }

        assertTrue(failure.isFailure)
    }
}
