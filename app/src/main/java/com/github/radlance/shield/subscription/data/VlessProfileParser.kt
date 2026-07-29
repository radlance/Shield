package com.github.radlance.shield.subscription.data

import com.github.radlance.shield.subscription.domain.ImportResult
import com.github.radlance.shield.subscription.domain.ProfileParser
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

class VlessProfileParser : ProfileParser {

    override fun parseSubscription(content: String, subscriptionId: String): ImportResult {
        val decoded = decodeBody(content)
        var rejected = 0
        val profiles = decoded
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { entry ->
                runCatching { parseVless(entry, subscriptionId) }
                    .onFailure { rejected++ }
                    .getOrNull()
            }
            .distinctBy(VlessProfile::id)
            .toList()
        return ImportResult(profiles, rejected)
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
            else -> error("Unsupported VLESS transport")
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
}
