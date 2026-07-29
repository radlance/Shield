package com.github.radlance.shield.vpn.data

import com.github.radlance.shield.subscription.domain.VlessProfile
import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigGeneratorTest {
    @Test
    fun createsTunOnlyRealityConfiguration() {
        val config = SingBoxConfigGenerator().generate(
            VlessProfile(
                id = "profile",
                subscriptionId = "subscription",
                name = "Node",
                server = "example.com",
                port = 443,
                uuid = "123e4567-e89b-42d3-a456-426614174000",
                transport = VlessTransport.GRPC,
                security = VlessSecurity.REALITY,
                serverName = "www.example.org",
                fingerprint = "chrome",
                realityPublicKey = "public-key",
                realityShortId = "abcd",
                grpcServiceName = "tunnel"
            )
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val inbounds = root.getValue("inbounds").jsonArray
        val outbounds = root.getValue("outbounds").jsonArray
        val dnsServers = root.getValue("dns").jsonObject.getValue("servers").jsonArray
        val route = root.getValue("route").jsonObject

        assertEquals("tun", inbounds.single().jsonObject.getValue("type").jsonPrimitive.content)
        assertTrue(inbounds.single().jsonObject.getValue("auto_route").jsonPrimitive.content.toBoolean())
        assertFalse(config.contains("\"type\":\"mixed\""))
        assertFalse(config.contains("\"type\":\"socks\""))
        assertEquals("vless", outbounds.first().jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("local", dnsServers.first().jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("local-dns", route.getValue("default_domain_resolver").jsonPrimitive.content)
        assertEquals(
            "reality",
            outbounds.first().jsonObject
                .getValue("tls").jsonObject
                .keys.first { it == "reality" }
        )
    }
}
