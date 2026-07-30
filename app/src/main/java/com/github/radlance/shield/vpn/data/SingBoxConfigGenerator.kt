package com.github.radlance.shield.vpn.data

import com.github.radlance.shield.subscription.domain.VlessProfile
import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import com.github.radlance.shield.vpn.routing.RoutingRuleSetPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class VpnRoutingConfig(
    val ruleSetPaths: RoutingRuleSetPaths? = null,
    val forceDirectDomains: Set<String> = emptySet(),
    val forceProxyDomains: Set<String> = emptySet()
)

class SingBoxConfigGenerator {
    private val json = Json { prettyPrint = false }

    fun generate(
        profile: VlessProfile,
        routing: VpnRoutingConfig = VpnRoutingConfig()
    ): String {
        val ruleSetPaths = routing.ruleSetPaths
        val forceProxyDomains = routing.forceProxyDomains.sorted()
        val forceDirectDomains =
            (routing.forceDirectDomains - routing.forceProxyDomains).sorted()
        val root = buildJsonObject {
            putJsonObject("log") {
                put("level", "info")
                put("timestamp", true)
            }
            putJsonArray("inbounds") {
                add(
                    buildJsonObject {
                        put("type", "tun")
                        put("tag", "tun-in")
                        putJsonArray("address") {
                            add(JsonPrimitive("172.19.0.1/30"))
                            add(JsonPrimitive("fdfe:dcba:9876::1/126"))
                        }
                        putJsonArray("route_address") {
                            add(JsonPrimitive("0.0.0.0/0"))
                            add(JsonPrimitive("::/0"))
                        }
                        put("mtu", 1400)
                        put("auto_route", true)
                        put("strict_route", true)
                        put("stack", "mixed")
                    }
                )
            }
            putJsonArray("outbounds") {
                add(vlessOutbound(profile))
                add(buildJsonObject {
                    put("type", "direct")
                    put("tag", "direct")
                })
            }
            putJsonObject("route") {
                putJsonArray("rules") {
                    add(buildJsonObject {
                        put("action", "sniff")
                    })
                    add(buildJsonObject {
                        put("protocol", "dns")
                        put("action", "hijack-dns")
                    })
                    add(buildJsonObject {
                        put("ip_is_private", true)
                        put("outbound", "direct")
                    })
                    if (forceProxyDomains.isNotEmpty()) {
                        add(buildJsonObject {
                            put("domain_suffix", forceProxyDomains.toJsonArray())
                            put("action", "route")
                            put("outbound", "proxy")
                        })
                    }
                    if (forceDirectDomains.isNotEmpty()) {
                        add(buildJsonObject {
                            put("domain_suffix", forceDirectDomains.toJsonArray())
                            put("action", "route")
                            put("outbound", "direct")
                        })
                    }
                    if (ruleSetPaths != null) {
                        add(buildJsonObject {
                            put(
                                "rule_set",
                                listOf(
                                    BLOCKED_DOMAINS_TAG,
                                    BLOCKED_IPS_TAG,
                                    BLOCKED_COMMUNITY_IPS_TAG
                                ).toJsonArray()
                            )
                            put("action", "route")
                            put("outbound", "proxy")
                        })
                        add(buildJsonObject {
                            put(
                                "rule_set",
                                listOf(
                                    AVAILABLE_ONLY_INSIDE_TAG,
                                    RUSSIAN_DOMAINS_TAG,
                                    RUSSIAN_IPS_TAG
                                ).toJsonArray()
                            )
                            put("action", "route")
                            put("outbound", "direct")
                        })
                    }
                }
                if (ruleSetPaths != null) {
                    putJsonArray("rule_set") {
                        add(localRuleSet(BLOCKED_DOMAINS_TAG, ruleSetPaths.blockedDomains))
                        add(localRuleSet(BLOCKED_IPS_TAG, ruleSetPaths.blockedIps))
                        add(
                            localRuleSet(
                                BLOCKED_COMMUNITY_IPS_TAG,
                                ruleSetPaths.blockedCommunityIps
                            )
                        )
                        add(
                            localRuleSet(
                                AVAILABLE_ONLY_INSIDE_TAG,
                                ruleSetPaths.availableOnlyInsideDomains
                            )
                        )
                        add(localRuleSet(RUSSIAN_DOMAINS_TAG, ruleSetPaths.russianDomains))
                        add(localRuleSet(RUSSIAN_IPS_TAG, ruleSetPaths.russianIps))
                    }
                }
                put("default_domain_resolver", "local-dns")
                put("auto_detect_interface", true)
                put("final", "proxy")
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    add(buildJsonObject {
                        put("type", "local")
                        put("tag", "local-dns")
                    })
                    add(buildJsonObject {
                        put("type", "tls")
                        put("tag", "remote-dns")
                        put("server", "1.1.1.1")
                        put("server_port", 853)
                        put("detour", "proxy")
                    })
                }
                if (
                    forceProxyDomains.isNotEmpty() ||
                    forceDirectDomains.isNotEmpty() ||
                    ruleSetPaths != null
                ) {
                    putJsonArray("rules") {
                        if (forceProxyDomains.isNotEmpty()) {
                            add(dnsDomainRule(forceProxyDomains, "remote-dns"))
                        }
                        if (forceDirectDomains.isNotEmpty()) {
                            add(dnsDomainRule(forceDirectDomains, "local-dns"))
                        }
                        if (ruleSetPaths != null) {
                            add(buildJsonObject {
                                put("rule_set", BLOCKED_DOMAINS_TAG)
                                put("action", "route")
                                put("server", "remote-dns")
                            })
                            add(buildJsonObject {
                                put(
                                    "rule_set",
                                    listOf(
                                        AVAILABLE_ONLY_INSIDE_TAG,
                                        RUSSIAN_DOMAINS_TAG
                                    ).toJsonArray()
                                )
                                put("action", "route")
                                put("server", "local-dns")
                            })
                        }
                    }
                }
                put("final", "remote-dns")
                put("strategy", "prefer_ipv4")
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun localRuleSet(tag: String, path: String) = buildJsonObject {
        put("type", "local")
        put("tag", tag)
        put("format", "binary")
        put("path", path)
    }

    private fun dnsDomainRule(domains: List<String>, server: String) = buildJsonObject {
        put("domain_suffix", domains.toJsonArray())
        put("action", "route")
        put("server", server)
    }

    private fun Collection<String>.toJsonArray(): JsonArray =
        buildJsonArray { forEach { add(JsonPrimitive(it)) } }

    private fun vlessOutbound(profile: VlessProfile) = buildJsonObject {
        put("type", "vless")
        put("tag", "proxy")
        put("server", profile.server)
        put("server_port", profile.port)
        put("uuid", profile.uuid)
        profile.flow?.let { put("flow", it) }
        profile.packetEncoding?.let { put("packet_encoding", it) }

        if (profile.security != VlessSecurity.NONE) {
            putJsonObject("tls") {
                put("enabled", true)
                put("server_name", profile.serverName ?: profile.server)
                if (profile.alpn.isNotEmpty()) {
                    put("alpn", buildJsonArray {
                        profile.alpn.forEach { add(JsonPrimitive(it)) }
                    })
                }
                profile.fingerprint?.let { fingerprint ->
                    putJsonObject("utls") {
                        put("enabled", true)
                        put("fingerprint", fingerprint)
                    }
                }
                if (profile.security == VlessSecurity.REALITY) {
                    putJsonObject("reality") {
                        put("enabled", true)
                        put("public_key", requireNotNull(profile.realityPublicKey))
                        profile.realityShortId?.let { put("short_id", it) }
                    }
                }
            }
        }

        when (profile.transport) {
            VlessTransport.TCP -> Unit
            VlessTransport.WEBSOCKET -> putJsonObject("transport") {
                put("type", "ws")
                profile.path?.let { put("path", it) }
                profile.host?.let { host ->
                    putJsonObject("headers") { put("Host", host) }
                }
            }
            VlessTransport.GRPC -> putJsonObject("transport") {
                put("type", "grpc")
                profile.grpcServiceName?.let { put("service_name", it) }
            }
        }
    }

    private companion object {
        const val BLOCKED_DOMAINS_TAG = "geosite-ru-blocked"
        const val BLOCKED_IPS_TAG = "geoip-ru-blocked"
        const val BLOCKED_COMMUNITY_IPS_TAG = "geoip-ru-blocked-community"
        const val AVAILABLE_ONLY_INSIDE_TAG = "geosite-ru-available-only-inside"
        const val RUSSIAN_DOMAINS_TAG = "geosite-category-ru"
        const val RUSSIAN_IPS_TAG = "geoip-ru"
    }
}
