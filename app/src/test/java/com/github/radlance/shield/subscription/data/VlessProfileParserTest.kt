package com.github.radlance.shield.subscription.data

import com.github.radlance.shield.subscription.domain.UnsupportedSubscriptionAppException
import com.github.radlance.shield.subscription.domain.UnsupportedSubscriptionFormatException
import com.github.radlance.shield.subscription.domain.ProxyProtocol
import com.github.radlance.shield.subscription.domain.SubscriptionDeviceLimitException
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
    fun rejectsProviderDeviceLimitPlaceholder() {
        val result = runCatching {
            parser.parseSubscription(
                """
                {
                  "outbounds": [{
                    "type": "vless",
                    "tag": "Лимит девайсов!",
                    "server": "0.0.0.0",
                    "server_port": 1,
                    "uuid": "00000000-0000-0000-0000-000000000000"
                  }]
                }
                """.trimIndent(),
                "subscription"
            )
        }

        assertTrue(result.exceptionOrNull() is SubscriptionDeviceLimitException)
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

    @Test
    fun parsesAllSupportedDirectProtocolLinks() {
        val vmessPayload = Base64.getEncoder().encodeToString(
            """{"v":"2","ps":"VMess","add":"vmess.example.com","port":"443","id":"123e4567-e89b-42d3-a456-426614174000","aid":"0","net":"ws","path":"/ws","host":"cdn.example.com","tls":"tls","sni":"vmess.example.com"}"""
                .toByteArray()
        )
        val shadowsocksCredentials = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-128-gcm:secret".toByteArray())
        val content = listOf(
            "vmess://$vmessPayload",
            "trojan://secret@trojan.example.com:443?security=tls&sni=trojan.example.com#Trojan",
            "ss://$shadowsocksCredentials@ss.example.com:8388#Shadowsocks",
            "hysteria2://secret@hy.example.com:443?sni=hy.example.com#Hysteria",
            "tuic://123e4567-e89b-42d3-a456-426614174000:secret@tuic.example.com:443?sni=tuic.example.com#TUIC"
        ).joinToString("\n")

        val result = parser.parseSubscription(content, "subscription")

        assertEquals(
            setOf(
                ProxyProtocol.VMESS,
                ProxyProtocol.TROJAN,
                ProxyProtocol.SHADOWSOCKS,
                ProxyProtocol.HYSTERIA2,
                ProxyProtocol.TUIC
            ),
            result.profiles.mapTo(linkedSetOf()) { it.protocol }
        )
        assertEquals(0, result.rejectedEntries)
        assertTrue(result.profiles.all { it.outboundJson != null })
    }

    @Test
    fun parsesMixedSingBoxOutbounds() {
        val result = parser.parseSubscription(
            """
            {
              "outbounds": [
                {"type":"selector","tag":"proxy","outbounds":["vmess","trojan"]},
                {"type":"vmess","tag":"vmess","server":"vmess.example.com","server_port":443,"uuid":"123e4567-e89b-42d3-a456-426614174000","security":"auto"},
                {"type":"trojan","tag":"trojan","server":"trojan.example.com","server_port":443,"password":"secret","tls":{"enabled":true}},
                {"type":"shadowsocks","tag":"ss","server":"ss.example.com","server_port":8388,"method":"aes-128-gcm","password":"secret"}
              ]
            }
            """.trimIndent(),
            "subscription"
        )

        assertEquals(3, result.profiles.size)
        assertEquals(
            setOf(ProxyProtocol.VMESS, ProxyProtocol.TROJAN, ProxyProtocol.SHADOWSOCKS),
            result.profiles.mapTo(hashSetOf()) { it.protocol }
        )
    }

    @Test
    fun parsesClashYamlSubscription() {
        val result = parser.parseSubscription(
            """
            proxies:
              - name: Trojan node
                type: trojan
                server: trojan.example.com
                port: 443
                password: secret
                sni: trojan.example.com
              - name: SS node
                type: ss
                server: ss.example.com
                port: 8388
                cipher: aes-128-gcm
                password: secret
            """.trimIndent(),
            "subscription"
        )

        assertEquals(2, result.profiles.size)
        assertEquals(
            setOf(ProxyProtocol.TROJAN, ProxyProtocol.SHADOWSOCKS),
            result.profiles.mapTo(hashSetOf()) { it.protocol }
        )
    }

    @Test
    fun parsesSip008ShadowsocksJson() {
        val result = parser.parseSubscription(
            """
            {
              "version": 1,
              "servers": [{
                "remarks": "SIP008",
                "server": "ss.example.com",
                "server_port": 8388,
                "method": "aes-128-gcm",
                "password": "secret"
              }]
            }
            """.trimIndent(),
            "subscription"
        )

        assertEquals(ProxyProtocol.SHADOWSOCKS, result.profiles.single().protocol)
        assertEquals("SIP008", result.profiles.single().name)
    }
}
