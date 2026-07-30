package com.github.radlance.shield.subscription.data

import com.github.radlance.shield.subscription.domain.UnsupportedSubscriptionAppException
import com.github.radlance.shield.subscription.domain.UnsupportedSubscriptionFormatException
import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun parsesSingBoxJsonRealityWebSocketOutbound() {
        val result = parser.parseSubscription(
            """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "Reality node",
                  "server": "edge.example.com",
                  "server_port": 443,
                  "uuid": "123e4567-e89b-42d3-a456-426614174000",
                  "flow": "xtls-rprx-vision",
                  "packet_encoding": "xudp",
                  "tls": {
                    "enabled": true,
                    "server_name": "www.example.org",
                    "alpn": ["h2", "http/1.1"],
                    "utls": {"enabled": true, "fingerprint": "chrome"},
                    "reality": {
                      "enabled": true,
                      "public_key": "public-key",
                      "short_id": "abcd"
                    }
                  },
                  "transport": {
                    "type": "ws",
                    "path": "/vpn",
                    "headers": {"Host": "cdn.example.org"}
                  }
                },
                {"type": "direct", "tag": "direct"}
              ]
            }
            """.trimIndent(),
            "subscription"
        )

        val profile = result.profiles.single()
        assertEquals("Reality node", profile.name)
        assertEquals(VlessTransport.WEBSOCKET, profile.transport)
        assertEquals(VlessSecurity.REALITY, profile.security)
        assertEquals("www.example.org", profile.serverName)
        assertEquals("chrome", profile.fingerprint)
        assertEquals("cdn.example.org", profile.host)
        assertEquals("/vpn", profile.path)
        assertEquals("xudp", profile.packetEncoding)
        assertEquals(0, result.rejectedEntries)
    }

    @Test
    fun reportsUnsupportedXhttpWithoutTreatingItAsTcp() {
        val result = parser.parseSubscription(
            """
            {
              "outbounds": [{
                "type": "vless",
                "tag": "XHTTP",
                "server": "example.com",
                "server_port": 443,
                "uuid": "123e4567-e89b-42d3-a456-426614174000",
                "transport": {"type": "xhttp", "path": "/vpn"}
              }]
            }
            """.trimIndent(),
            "subscription"
        )

        assertTrue(result.profiles.isEmpty())
        assertEquals(setOf("xhttp"), result.unsupportedTransports)
        assertEquals(1, result.rejectedEntries)
    }

    @Test
    fun rejectsProviderUnsupportedApplicationPlaceholder() {
        val result = runCatching {
            parser.parseSubscription(
                "vless://123e4567-e89b-42d3-a456-426614174000@[::1]:443" +
                    "#%D0%9F%D0%A0%D0%98%D0%9B%D0%9E%D0%96%D0%95%D0%9D%D0%98%D0%95+" +
                    "%D0%9D%D0%95+%D0%9F%D0%9E%D0%94%D0%94%D0%95%D0%A0%D0%96%D0%98%D0%92%D0%90%D0%95%D0%A2%D0%A1%D0%AF",
                "subscription"
            )
        }

        assertTrue(result.exceptionOrNull() is UnsupportedSubscriptionAppException)
    }

    @Test
    fun rejectsInstallationHtml() {
        val result = runCatching {
            parser.parseSubscription(
                "<!doctype html><html><head></head><body>Install</body></html>",
                "subscription"
            )
        }

        assertTrue(result.exceptionOrNull() is UnsupportedSubscriptionFormatException)
        assertFalse(result.isSuccess)
    }
}
