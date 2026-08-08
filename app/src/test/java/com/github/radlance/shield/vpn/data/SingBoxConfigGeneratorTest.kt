package com.github.radlance.shield.vpn.data

import com.github.radlance.shield.subscription.domain.VlessProfile
import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import com.github.radlance.shield.subscription.domain.ProxyProtocol
import com.github.radlance.shield.vpn.routing.RoutingRuleSetPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigGeneratorTest {
    @Test
    fun insertsCanonicalNonVlessOutboundAsProxy() {
        val config = SingBoxConfigGenerator().generate(
            VlessProfile(
                id = "trojan-id",
                subscriptionId = "subscription",
                name = "Trojan",
                server = "trojan.example.com",
                port = 443,
                protocol = ProxyProtocol.TROJAN,
                outboundJson = """{"type":"trojan","tag":"provider-tag","server":"trojan.example.com","server_port":443,"password":"secret","tls":{"enabled":true}}"""
            )
        )

        val root = Json.parseToJsonElement(config).jsonObject
        val outbound = root["outbounds"]!!.jsonArray.first().jsonObject
        assertEquals("trojan", outbound["type"]!!.jsonPrimitive.content)
        assertEquals("proxy", outbound["tag"]!!.jsonPrimitive.content)
        assertEquals("secret", outbound["password"]!!.jsonPrimitive.content)
    }

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
        val tun = inbounds.single().jsonObject
        val outbounds = root.getValue("outbounds").jsonArray
        val dns = root.getValue("dns").jsonObject
        val dnsServers = dns.getValue("servers").jsonArray
        val route = root.getValue("route").jsonObject

        assertEquals("tun", tun.getValue("type").jsonPrimitive.content)
        assertTrue(tun.getValue("auto_route").jsonPrimitive.content.toBoolean())
        assertEquals("1400", tun.getValue("mtu").jsonPrimitive.content)
        assertEquals(
            listOf("0.0.0.0/0", "::/0"),
            tun.getValue("route_address").jsonArray.map { it.jsonPrimitive.content }
        )
        assertFalse(config.contains("\"type\":\"mixed\""))
        assertFalse(config.contains("\"type\":\"socks\""))
        assertEquals("vless", outbounds.first().jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("local", dnsServers.first().jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("https", dnsServers.last().jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("443", dnsServers.last().jsonObject.getValue("server_port").jsonPrimitive.content)
        assertEquals(
            "cloudflare-dns.com",
            dnsServers.last().jsonObject.getValue("tls").jsonObject
                .getValue("server_name").jsonPrimitive.content
        )
        assertEquals("local-dns", route.getValue("default_domain_resolver").jsonPrimitive.content)
        assertTrue(dns.getValue("independent_cache").jsonPrimitive.content.toBoolean())
        assertTrue(dns.getValue("reverse_mapping").jsonPrimitive.content.toBoolean())
        assertEquals(
            "reality",
            outbounds.first().jsonObject
                .getValue("tls").jsonObject
                .keys.first { it == "reality" }
        )
    }

    @Test
    fun ordersBlockedRulesBeforeRussianDirectRules() {
        val config = SingBoxConfigGenerator().generate(
            profile = testProfile(),
            routing = VpnRoutingConfig(
                ruleSetPaths = testRuleSetPaths(),
                forceDirectDomains = setOf("direct.example"),
                forceProxyDomains = setOf("proxy.example")
            )
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val route = root.getValue("route").jsonObject
        val dns = root.getValue("dns").jsonObject
        val routeRules = route.getValue("rules").jsonArray.map { it.jsonObject }
        val blockedIndex = routeRules.indexOfFirst { rule ->
            (rule["rule_set"] as? JsonPrimitive)?.content == "geosite-ru-blocked"
        }
        val russianIndex = routeRules.indexOfFirst { rule ->
            (rule["rule_set"] as? JsonPrimitive)?.content == "geosite-category-ru"
        }
        val ipv6FallbackIndex = routeRules.indexOfFirst { rule ->
            rule["ip_version"]?.jsonPrimitive?.content == "6"
        }
        val ruleSets = route.getValue("rule_set").jsonArray.map { it.jsonObject }

        assertTrue(blockedIndex >= 0)
        assertTrue(russianIndex > blockedIndex)
        assertTrue(ipv6FallbackIndex in 0 until blockedIndex)
        assertEquals(
            "proxy",
            routeRules[ipv6FallbackIndex].getValue("outbound").jsonPrimitive.content
        )
        assertEquals("proxy", routeRules[blockedIndex].getValue("outbound").jsonPrimitive.content)
        assertEquals("direct", routeRules[russianIndex].getValue("outbound").jsonPrimitive.content)
        assertEquals(6, ruleSets.size)
        assertEquals(
            "/routing/geosite-ru-blocked.srs",
            ruleSets.first().getValue("path").jsonPrimitive.content
        )
        val dnsRules = dns.getValue("rules").jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("remote-dns", "local-dns", "local-dns", "remote-dns", "local-dns"),
            dnsRules.map { it.getValue("server").jsonPrimitive.content }
        )
        assertEquals(
            listOf("ipv4_only", "ipv4_only", "ipv4_only"),
            dnsRules.mapNotNull { it["strategy"]?.jsonPrimitive?.content }
        )
        assertEquals("proxy", route.getValue("final").jsonPrimitive.content)
        assertEquals("remote-dns", dns.getValue("final").jsonPrimitive.content)
    }

    @Test
    fun keepsDomainOverridesWhenSmartRulesAreDisabled() {
        val config = SingBoxConfigGenerator().generate(
            profile = testProfile(),
            routing = VpnRoutingConfig(
                ruleSetPaths = null,
                forceDirectDomains = setOf("bank.example"),
                forceProxyDomains = setOf("blocked.example", "bank.example")
            )
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val route = root.getValue("route").jsonObject
        val rules = route.getValue("rules").jsonArray.map { it.jsonObject }
        val domainRules = rules.filter {
            "domain_suffix" in it && it["action"]?.jsonPrimitive?.content == "route"
        }

        assertFalse(config.contains("geosite-category-ru"))
        assertFalse("rule_set" in route)
        assertEquals(1, domainRules.size)
        assertEquals("proxy", domainRules.single().getValue("outbound").jsonPrimitive.content)
        assertEquals(
            listOf("bank.example", "blocked.example"),
            domainRules.single().getValue("domain_suffix").jsonArray
                .map { it.jsonPrimitive.content }
        )
    }

    @Test
    fun evaluatesDomainPoliciesBeforeIpPolicies() {
        val config = SingBoxConfigGenerator().generate(
            profile = testProfile(),
            routing = VpnRoutingConfig(ruleSetPaths = testRuleSetPaths())
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val routeRules = root.getValue("route").jsonObject.getValue("rules")
            .jsonArray.map { it.jsonObject }
        val dnsRules = root.getValue("dns").jsonObject.getValue("rules")
            .jsonArray.map { it.jsonObject }
        val insideOnlyIndex = routeRules.indexOfFirst { rule ->
            (rule["rule_set"] as? JsonPrimitive)?.content ==
                "geosite-ru-available-only-inside"
        }
        val blockedDomainIndex = routeRules.indexOfFirst { rule ->
            (rule["rule_set"] as? JsonPrimitive)?.content == "geosite-ru-blocked"
        }
        val russianDomainIndex = routeRules.indexOfFirst { rule ->
            (rule["rule_set"] as? JsonPrimitive)?.content == "geosite-category-ru"
        }
        val blockedIpIndex = routeRules.indexOfFirst { rule ->
            (rule["rule_set"] as? JsonArray)?.any {
                it.jsonPrimitive.content == "geoip-ru-blocked"
            } == true
        }
        val russianIpIndex = routeRules.indexOfFirst { rule ->
            (rule["rule_set"] as? JsonPrimitive)?.content == "geoip-ru"
        }
        val ipv6FallbackIndex = routeRules.indexOfFirst { rule ->
            rule["ip_version"]?.jsonPrimitive?.content == "6"
        }

        assertTrue(insideOnlyIndex >= 0)
        assertTrue(blockedDomainIndex > insideOnlyIndex)
        assertTrue(ipv6FallbackIndex in 0 until insideOnlyIndex)
        assertTrue(blockedIpIndex > blockedDomainIndex)
        assertTrue(russianDomainIndex > blockedIpIndex)
        assertTrue(russianIpIndex > blockedIpIndex)
        assertFalse(routeRules.any { rule ->
            rule["network"]?.jsonPrimitive?.content == "udp" &&
                rule["port"]?.jsonPrimitive?.content == "443"
        })
        assertFalse(routeRules.any { it["action"]?.jsonPrimitive?.content == "reject" })
        assertEquals(
            listOf("local-dns", "remote-dns", "local-dns"),
            dnsRules.map { it.getValue("server").jsonPrimitive.content }
        )
        assertEquals(
            listOf("ipv4_only", null, "ipv4_only"),
            dnsRules.map { it["strategy"]?.jsonPrimitive?.content }
        )
    }

    private fun testRuleSetPaths() = RoutingRuleSetPaths(
        blockedDomains = "/routing/geosite-ru-blocked.srs",
        blockedIps = "/routing/geoip-ru-blocked.srs",
        blockedCommunityIps = "/routing/geoip-ru-blocked-community.srs",
        availableOnlyInsideDomains = "/routing/geosite-ru-available-only-inside.srs",
        russianDomains = "/routing/geosite-category-ru.srs",
        russianIps = "/routing/geoip-ru.srs"
    )

    private fun testProfile() = VlessProfile(
        id = "profile",
        subscriptionId = "subscription",
        name = "Node",
        server = "example.com",
        port = 443,
        uuid = "123e4567-e89b-42d3-a456-426614174000",
        security = VlessSecurity.REALITY,
        serverName = "www.example.org",
        realityPublicKey = "public-key"
    )
}
