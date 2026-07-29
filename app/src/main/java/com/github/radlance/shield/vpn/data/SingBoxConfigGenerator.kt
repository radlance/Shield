package com.github.radlance.shield.vpn.data

import com.github.radlance.shield.subscription.domain.VlessProfile
import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class SingBoxConfigGenerator {
    private val json = Json { prettyPrint = false }

    fun generate(profile: VlessProfile): String {
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
                            add(kotlinx.serialization.json.JsonPrimitive("172.19.0.1/30"))
                            add(kotlinx.serialization.json.JsonPrimitive("fdfe:dcba:9876::1/126"))
                        }
                        putJsonArray("route_address") {
                            add(kotlinx.serialization.json.JsonPrimitive("0.0.0.0/0"))
                            add(kotlinx.serialization.json.JsonPrimitive("::/0"))
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
                put("final", "remote-dns")
                put("strategy", "prefer_ipv4")
            }
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }

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
                        profile.alpn.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
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
}
