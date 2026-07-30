package com.github.radlance.shield.subscription.data

import com.github.radlance.shield.subscription.domain.ImportResult
import com.github.radlance.shield.subscription.domain.ProfileParser
import com.github.radlance.shield.subscription.domain.UnsupportedSubscriptionAppException
import com.github.radlance.shield.subscription.domain.UnsupportedSubscriptionFormatException
import com.github.radlance.shield.subscription.domain.VlessProfile
import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

class VlessProfileParser : ProfileParser {
    private val json = Json { ignoreUnknownKeys = true }

    override fun parseSubscription(content: String, subscriptionId: String): ImportResult {
        val decoded = decodeBody(content)
        if (looksLikeHtml(decoded)) {
            throw UnsupportedSubscriptionFormatException(
                "The subscription URL returned an installation page instead of a configuration"
            )
        }
        if (decoded.trimStart().startsWith('{')) {
            return parseSingBoxJson(decoded, subscriptionId)
        }

        var rejected = 0
        val unsupportedTransports = mutableSetOf<String>()
        val profiles = decoded
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { entry ->
                try {
                    parseVless(entry, subscriptionId).also(::requireRealProfile)
                } catch (error: UnsupportedTransportException) {
                    rejected++
                    unsupportedTransports += error.transport
                    null
                } catch (error: UnsupportedSubscriptionAppException) {
                    throw error
                } catch (_: Exception) {
                    rejected++
                    null
                }
            }
            .distinctBy(VlessProfile::id)
            .toList()
        return ImportResult(profiles, rejected, unsupportedTransports)
    }

    override fun parseVless(link: String, subscriptionId: String): VlessProfile {
        require(link.startsWith("vless://", ignoreCase = true)) { "Only vless:// links are supported" }
        val uri = URI(link.trim())
        val uuid = uri.userInfo?.substringBefore(':') ?: error("Missing VLESS UUID")
        UUID.fromString(uuid)
        val server = uri.host ?: parseBracketedHost(uri.rawAuthority) ?: error("Missing server")
        val port = uri.port.takeIf { it in 1..65535 } ?: error("Invalid port")
        val query = parseQuery(uri.rawQuery)
        val transport = when (query["type"]?.lowercase() ?: "tcp") {
            "tcp", "raw" -> VlessTransport.TCP
            "ws", "websocket" -> VlessTransport.WEBSOCKET
            "grpc" -> VlessTransport.GRPC
            else -> throw UnsupportedTransportException(query["type"].orEmpty())
        }
        val security = when (query["security"]?.lowercase() ?: "none") {
            "", "none" -> VlessSecurity.NONE
            "tls" -> VlessSecurity.TLS
            "reality" -> VlessSecurity.REALITY
            else -> error("Unsupported VLESS security")
        }
        if (security == VlessSecurity.REALITY) {
            require(!query["pbk"].isNullOrBlank()) { "REALITY public key is required" }
            require(!query["sni"].isNullOrBlank()) { "REALITY server name is required" }
        }
        val name = decode(uri.rawFragment).ifBlank { server }
        val normalized = buildString {
            append(uuid.lowercase())
            append('|')
            append(server.lowercase())
            append('|')
            append(port)
            query.toSortedMap().forEach { (key, value) ->
                append('|')
                append(key.lowercase())
                append('=')
                append(value)
            }
        }
        return VlessProfile(
            id = sha256(normalized),
            subscriptionId = subscriptionId,
            name = name,
            server = server,
            port = port,
            uuid = uuid.lowercase(),
            transport = transport,
            security = security,
            flow = query["flow"]?.takeIf(String::isNotBlank),
            serverName = query["sni"]?.takeIf(String::isNotBlank),
            alpn = query["alpn"]?.split(',')?.filter(String::isNotBlank).orEmpty(),
            fingerprint = query["fp"]?.takeIf(String::isNotBlank),
            realityPublicKey = query["pbk"]?.takeIf(String::isNotBlank),
            realityShortId = query["sid"]?.takeIf(String::isNotBlank),
            path = query["path"]?.takeIf(String::isNotBlank),
            host = query["host"]?.takeIf(String::isNotBlank),
            grpcServiceName = query["serviceName"]?.takeIf(String::isNotBlank),
            packetEncoding = query["packetEncoding"]?.takeIf(String::isNotBlank)
        )
    }

    private fun parseSingBoxJson(content: String, subscriptionId: String): ImportResult {
        val root = runCatching { json.parseToJsonElement(content) }
            .getOrElse {
                throw UnsupportedSubscriptionFormatException(
                    "The subscription contains invalid JSON"
                )
            } as? JsonObject ?: throw UnsupportedSubscriptionFormatException(
            "The subscription JSON root must be an object"
        )
        val outbounds = root["outbounds"] as? JsonArray
            ?: throw UnsupportedSubscriptionFormatException(
                "The subscription JSON does not contain Sing-box outbounds"
            )
        var rejected = 0
        val unsupportedTransports = mutableSetOf<String>()
        val profiles = buildList {
            outbounds.forEach { element ->
                val outbound = element as? JsonObject ?: return@forEach
                if (outbound.string("type")?.lowercase() != "vless") return@forEach
                try {
                    add(parseSingBoxVless(outbound, subscriptionId).also(::requireRealProfile))
                } catch (error: UnsupportedTransportException) {
                    rejected++
                    unsupportedTransports += error.transport
                } catch (error: UnsupportedSubscriptionAppException) {
                    throw error
                } catch (_: Exception) {
                    rejected++
                }
            }
        }.distinctBy(VlessProfile::id)
        return ImportResult(profiles, rejected, unsupportedTransports)
    }

    private fun parseSingBoxVless(
        outbound: JsonObject,
        subscriptionId: String
    ): VlessProfile {
        val server = outbound.string("server")?.takeIf(String::isNotBlank)
            ?: error("Missing server")
        val port = outbound.int("server_port")?.takeIf { it in 1..65535 }
            ?: error("Invalid port")
        val uuid = outbound.string("uuid")?.also { UUID.fromString(it) }
            ?: error("Missing VLESS UUID")
        val transportObject = outbound["transport"] as? JsonObject
        val transportName = transportObject?.string("type")?.lowercase().orEmpty()
        val transport = when (transportName) {
            "", "tcp", "raw" -> VlessTransport.TCP
            "ws", "websocket" -> VlessTransport.WEBSOCKET
            "grpc" -> VlessTransport.GRPC
            else -> throw UnsupportedTransportException(transportName)
        }

        val tls = outbound["tls"] as? JsonObject
        val tlsEnabled = tls?.boolean("enabled") == true
        val reality = tls?.get("reality") as? JsonObject
        val realityEnabled = reality?.boolean("enabled") == true
        val security = when {
            realityEnabled -> VlessSecurity.REALITY
            tlsEnabled -> VlessSecurity.TLS
            else -> VlessSecurity.NONE
        }
        val serverName = tls?.string("server_name")?.takeIf(String::isNotBlank)
        val realityPublicKey = reality?.string("public_key")?.takeIf(String::isNotBlank)
        if (security == VlessSecurity.REALITY) {
            require(!serverName.isNullOrBlank()) { "REALITY server name is required" }
            require(!realityPublicKey.isNullOrBlank()) { "REALITY public key is required" }
        }

        val host = (transportObject?.get("headers") as? JsonObject)
            ?.entries
            ?.firstOrNull { (key, _) -> key.equals("host", ignoreCase = true) }
            ?.value
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
        val alpn = when (val value = tls?.get("alpn")) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> value.contentOrNull
                ?.split(',')
                ?.map(String::trim)
                .orEmpty()
            else -> emptyList()
        }.filter(String::isNotBlank)
        val fingerprint = (tls?.get("utls") as? JsonObject)
            ?.takeIf { it.boolean("enabled") != false }
            ?.string("fingerprint")
            ?.takeIf(String::isNotBlank)
        val name = outbound.string("tag")?.takeIf(String::isNotBlank) ?: server
        val normalized = listOf(
            uuid.lowercase(),
            server.lowercase(),
            port.toString(),
            transportName.ifBlank { "tcp" },
            security.name,
            outbound.string("flow").orEmpty(),
            serverName.orEmpty(),
            alpn.joinToString(","),
            fingerprint.orEmpty(),
            realityPublicKey.orEmpty(),
            reality?.string("short_id").orEmpty(),
            transportObject?.string("path").orEmpty(),
            host.orEmpty(),
            transportObject?.string("service_name").orEmpty(),
            outbound.string("packet_encoding").orEmpty()
        ).joinToString("|")

        return VlessProfile(
            id = sha256(normalized),
            subscriptionId = subscriptionId,
            name = name,
            server = server,
            port = port,
            uuid = uuid.lowercase(),
            transport = transport,
            security = security,
            flow = outbound.string("flow")?.takeIf(String::isNotBlank),
            serverName = serverName,
            alpn = alpn,
            fingerprint = fingerprint,
            realityPublicKey = realityPublicKey,
            realityShortId = reality?.string("short_id")?.takeIf(String::isNotBlank),
            path = transportObject?.string("path")?.takeIf(String::isNotBlank),
            host = host,
            grpcServiceName = transportObject
                ?.string("service_name")
                ?.takeIf(String::isNotBlank),
            packetEncoding = outbound.string("packet_encoding")?.takeIf(String::isNotBlank)
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBody(content: String): String {
        val trimmed = content.trim().removePrefix("\uFEFF")
        if (trimmed.lineSequence().any { it.trim().startsWith("vless://", true) }) return trimmed
        val compact = trimmed.filterNot(Char::isWhitespace)
        val decoded = sequenceOf(
            Base64.Default,
            Base64.UrlSafe
        ).mapNotNull { decoder ->
            runCatching {
                val padding = "=".repeat((4 - compact.length % 4) % 4)
                decoder.decode(compact + padding).decodeToString()
            }.getOrNull()
        }.firstOrNull { it.contains("vless://", ignoreCase = true) }
        return decoded ?: trimmed
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> =
        rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotBlank)
            .associate { part ->
                val key = part.substringBefore('=')
                decode(key) to decode(part.substringAfter('=', ""))
            }

    private fun parseBracketedHost(authority: String?): String? =
        authority?.substringAfter('@')?.takeIf { it.startsWith('[') }?.substringAfter('[')?.substringBefore(']')

    private fun decode(value: String?): String =
        URLDecoder.decode(value.orEmpty(), StandardCharsets.UTF_8.name())

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun requireRealProfile(profile: VlessProfile) {
        val normalizedName = profile.name
            .lowercase()
            .replace('ё', 'е')
        val unsupportedName = listOf(
            "приложение не поддерживается",
            "приложение не поддерживает",
            "application is not supported",
            "app is not supported",
            "unsupported application",
            "unsupported client"
        ).any(normalizedName::contains)
        if (unsupportedName) {
            throw UnsupportedSubscriptionAppException()
        }
    }

    private fun looksLikeHtml(value: String): Boolean {
        val trimmed = value.trimStart().lowercase()
        return trimmed.startsWith("<!doctype html") ||
            trimmed.startsWith("<html") ||
            ("<head" in trimmed && "<body" in trimmed)
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? =
        (get(name) as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(name: String): Boolean? =
        (get(name) as? JsonPrimitive)?.booleanOrNull

    private class UnsupportedTransportException(
        val transport: String
    ) : IllegalArgumentException("Unsupported VLESS transport: $transport")
}
