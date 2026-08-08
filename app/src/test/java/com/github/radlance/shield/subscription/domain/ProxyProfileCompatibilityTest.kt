package com.github.radlance.shield.subscription.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyProfileCompatibilityTest {
    @Test
    fun decodesLegacyVlessProfileWithProtocolDefaults() {
        val legacy = """
            {
              "id":"profile",
              "subscriptionId":"subscription",
              "name":"Legacy",
              "server":"example.com",
              "port":443,
              "uuid":"123e4567-e89b-42d3-a456-426614174000",
              "transport":"WEBSOCKET",
              "security":"TLS",
              "path":"/vpn"
            }
        """.trimIndent()

        val profile = Json.decodeFromString(ProxyProfile.serializer(), legacy)

        assertEquals(ProxyProtocol.VLESS, profile.protocol)
        assertEquals(VlessTransport.WEBSOCKET, profile.transport)
        assertEquals(VlessSecurity.TLS, profile.security)
        assertNull(profile.outboundJson)
    }
}
