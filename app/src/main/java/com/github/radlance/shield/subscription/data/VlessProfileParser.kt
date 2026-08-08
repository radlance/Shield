package com.github.radlance.shield.subscription.data

import com.github.radlance.shield.subscription.domain.ImportResult
import com.github.radlance.shield.subscription.domain.ProfileParser
import com.github.radlance.shield.subscription.domain.ProxyProfile
import com.github.radlance.shield.subscription.domain.ProxyProtocol
import com.github.radlance.shield.subscription.domain.SubscriptionDeviceLimitException
import com.github.radlance.shield.subscription.domain.UnsupportedSubscriptionAppException
import com.github.radlance.shield.subscription.domain.UnsupportedSubscriptionFormatException
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class UniversalProfileParser : ProfileParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val yaml = Load(
        LoadSettings.builder()
            .setAllowDuplicateKeys(false)
            .setMaxAliasesForCollections(50)
            .build()
    )

    override fun parseSubscription(content: String, subscriptionId: String): ImportResult {
        require(content.length <= MAX_SUBSCRIPTION_LENGTH) { "Subscription response is too large" }
        val decoded = decodeBody(content)
        if (looksLikeHtml(decoded)) {
            throw UnsupportedSubscriptionFormatException(
                "The subscription URL returned an installation page instead of a configuration"
            )
        }
        val trimmed = decoded.trimStart()
        return when {
            trimmed.startsWith('{') || trimmed.startsWith('[') ->
                parseJsonSubscription(trimmed, subscriptionId)
            looksLikeClashYaml(trimmed) -> parseClashYaml(trimmed, subscriptionId)
            else -> parseLinkList(trimmed, subscriptionId)
        }
    }

    override fun parseLink(link: String, subscriptionId: String): ProxyProfile {
        val trimmed = link.trim()
        return when (trimmed.substringBefore(':').lowercase()) {
            "vless" -> parseVlessUri(trimmed, subscriptionId)
            "vmess" -> parseVmessUri(trimmed, subscriptionId)
            "trojan" -> parseTrojanUri(trimmed, subscriptionId)
            "ss" -> parseShadowsocksUri(trimmed, subscriptionId)
            "hysteria2", "hy2" -> parseHysteria2Uri(trimmed, subscriptionId)
            "tuic" -> parseTuicUri(trimmed, subscriptionId)
            else -> error("Unsupported proxy link")
        }
    }

    override fun parseVless(link: String, subscriptionId: String): ProxyProfile {
        require(link.startsWith("vless://", ignoreCase = true)) { "Only vless:// links are supported" }
        return parseVlessUri(link.trim(), subscriptionId)
    }

    private fun parseLinkList(content: String, subscriptionId: String): ImportResult {
        var rejected = 0
        val unsupported = mutableSetOf<String>()
        val profiles = content.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') && !it.startsWith("//") }
            .mapNotNull { entry ->
                val scheme = entry.substringBefore(':', "").lowercase()
                if (scheme !in SUPPORTED_SCHEMES) {
                    rejected++
                    if (scheme.isNotBlank()) unsupported += scheme
                    return@mapNotNull null
                }
                runCatching { parseLink(entry, subscriptionId).also(::requireRealProfile) }
                    .getOrElse { error ->
                        if (
                            error is UnsupportedSubscriptionAppException ||
                            error is SubscriptionDeviceLimitException
                        ) throw error
                        rejected++
                        null
                    }
            }
            .distinctBy(ProxyProfile::id)
            .toList()
        return ImportResult(profiles, rejected, unsupported)
    }

    private fun parseJsonSubscription(content: String, subscriptionId: String): ImportResult {
        val root = runCatching { json.parseToJsonElement(content) }.getOrElse {
            throw UnsupportedSubscriptionFormatException("The subscription contains invalid JSON")
        }
        val entries = when (root) {
            is JsonObject -> when {
                root["outbounds"] is JsonArray -> (root["outbounds"] as JsonArray).toList()
                root["servers"] is JsonArray -> (root["servers"] as JsonArray).map { server ->
                    sip008Outbound(server as? JsonObject ?: JsonObject(emptyMap()))
                }
                root["proxies"] is JsonArray -> (root["proxies"] as JsonArray).toList()
                root.string("type") != null -> listOf(root)
                else -> throw UnsupportedSubscriptionFormatException(
                    "The subscription JSON contains no supported outbounds"
                )
            }
            is JsonArray -> root.map { element ->
                val objectValue = element as? JsonObject ?: return@map element
                if (objectValue.string("method") != null && objectValue.string("type") == null) {
                    sip008Outbound(objectValue)
                } else {
                    objectValue
                }
            }
            else -> throw UnsupportedSubscriptionFormatException(
                "The subscription JSON root must be an object or array"
            )
        }
        return parseOutboundEntries(entries, subscriptionId)
    }

    private fun parseOutboundEntries(
        entries: List<JsonElement>,
        subscriptionId: String
    ): ImportResult {
        var rejected = 0
        val unsupported = mutableSetOf<String>()
        val profiles = buildList {
            entries.forEach { element ->
                val outbound = element as? JsonObject ?: run {
                    rejected++
                    return@forEach
                }
                val type = outbound.string("type")?.lowercase().orEmpty()
                if (type !in SUPPORTED_OUTBOUND_TYPES) {
                    if (type !in IGNORED_OUTBOUND_TYPES) {
                        rejected++
                        if (type.isNotBlank()) unsupported += type
                    }
                    return@forEach
                }
                try {
                    add(profileFromOutbound(outbound, subscriptionId).also(::requireRealProfile))
                } catch (error: UnsupportedTransportException) {
                    rejected++
                    unsupported += error.transport
                } catch (error: UnsupportedSubscriptionAppException) {
                    throw error
                } catch (error: SubscriptionDeviceLimitException) {
                    throw error
                } catch (_: Exception) {
                    rejected++
                }
            }
        }.distinctBy(ProxyProfile::id)
        return ImportResult(profiles, rejected, unsupported)
    }

    private fun profileFromOutbound(
        original: JsonObject,
        subscriptionId: String
    ): ProxyProfile {
        val type = original.string("type")?.lowercase() ?: error("Missing outbound type")
        val protocol = protocolFor(type)
        val server = original.string("server")?.takeIf(String::isNotBlank)
            ?: error("Missing server")
        val port = original.int("server_port")?.takeIf { it in 1..65535 }
            ?: error("Invalid port")
        validateRequiredFields(original, protocol)
        val name = original.string("tag")?.takeIf(String::isNotBlank) ?: server
        val transportName = (original["transport"] as? JsonObject)
            ?.string("type")
            ?.lowercase()
            .orEmpty()
        if (
            protocol in setOf(ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TROJAN) &&
            transportName !in SUPPORTED_TRANSPORT_TYPES
        ) {
            throw UnsupportedTransportException(transportName)
        }
        val sanitized = JsonObject(
            original.filterKeys { it !in PROVIDER_REFERENCE_FIELDS } +
                ("type" to JsonPrimitive(type))
        )
        val canonical = json.encodeToString(JsonObject.serializer(), sanitized)
        val transportObject = original["transport"] as? JsonObject
        val tls = original["tls"] as? JsonObject
        val reality = tls?.get("reality") as? JsonObject
        val security = when {
            reality?.boolean("enabled") == true -> VlessSecurity.REALITY
            tls?.boolean("enabled") == true -> VlessSecurity.TLS
            else -> VlessSecurity.NONE
        }
        val transport = when (transportObject?.string("type")?.lowercase()) {
            "ws", "websocket" -> VlessTransport.WEBSOCKET
            "grpc" -> VlessTransport.GRPC
            else -> VlessTransport.TCP
        }
        return ProxyProfile(
            id = sha256(canonical),
            subscriptionId = subscriptionId,
            name = name,
            server = server,
            port = port,
            uuid = original.string("uuid").orEmpty().lowercase(),
            protocol = protocol,
            outboundJson = canonical,
            transport = transport,
            security = security,
            flow = original.string("flow")?.takeIf(String::isNotBlank),
            serverName = tls?.string("server_name")?.takeIf(String::isNotBlank),
            alpn = tls?.stringList("alpn").orEmpty(),
            fingerprint = (tls?.get("utls") as? JsonObject)?.string("fingerprint"),
            realityPublicKey = reality?.string("public_key"),
            realityShortId = reality?.string("short_id"),
            path = transportObject?.string("path"),
            host = (transportObject?.get("headers") as? JsonObject)?.stringIgnoreCase("host"),
            grpcServiceName = transportObject?.string("service_name"),
            packetEncoding = original.string("packet_encoding")
        )
    }

    private fun validateRequiredFields(outbound: JsonObject, protocol: ProxyProtocol) {
        when (protocol) {
            ProxyProtocol.VLESS,
            ProxyProtocol.VMESS -> outbound.string("uuid")
                ?.also { UUID.fromString(it) }
                ?: error("Missing UUID")
            ProxyProtocol.TROJAN,
            ProxyProtocol.HYSTERIA2 -> require(!outbound.string("password").isNullOrBlank()) {
                "Missing password"
            }
            ProxyProtocol.SHADOWSOCKS -> {
                require(!outbound.string("method").isNullOrBlank()) { "Missing method" }
                require(!outbound.string("password").isNullOrBlank()) { "Missing password" }
            }
            ProxyProtocol.TUIC -> {
                outbound.string("uuid")?.also { UUID.fromString(it) } ?: error("Missing UUID")
                require(!outbound.string("password").isNullOrBlank()) { "Missing password" }
            }
        }
    }

    private fun parseVlessUri(link: String, subscriptionId: String): ProxyProfile {
        val uri = URI(link)
        val uuid = uri.userInfo?.substringBefore(':')?.also { UUID.fromString(it) }
            ?: error("Missing VLESS UUID")
        val query = parseQuery(uri.rawQuery)
        val outbound = commonUriOutbound("vless", uri, query) {
            put("uuid", uuid.lowercase())
            query["flow"]?.takeIf(String::isNotBlank)?.let { put("flow", it) }
            query["packetEncoding"]?.takeIf(String::isNotBlank)?.let { put("packet_encoding", it) }
            addTlsAndTransport(query, uri)
        }
        return profileFromOutbound(outbound, subscriptionId)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseVmessUri(link: String, subscriptionId: String): ProxyProfile {
        val encoded = link.substringAfter("vmess://").trim()
        val decoded = decodeBase64(encoded) ?: error("Invalid VMess payload")
        val source = json.parseToJsonElement(decoded) as? JsonObject ?: error("Invalid VMess JSON")
        val server = source.string("add") ?: source.string("server") ?: error("Missing server")
        val port = source.flexibleInt("port")?.takeIf { it in 1..65535 } ?: error("Invalid port")
        val uuid = source.string("id")?.also { UUID.fromString(it) } ?: error("Missing UUID")
        val network = source.string("net")?.lowercase().orEmpty()
        val outbound = buildJsonObject {
            put("type", "vmess")
            put("tag", source.string("ps") ?: server)
            put("server", server)
            put("server_port", port)
            put("uuid", uuid.lowercase())
            source.flexibleInt("aid")?.let { put("alter_id", it) }
            source.string("scy")?.takeIf(String::isNotBlank)?.let { put("security", it) }
            addTls(source.string("tls"), source.string("sni"), source.string("alpn"), source.string("fp"))
            addTransport(
                type = network,
                path = source.string("path"),
                host = source.string("host"),
                serviceName = source.string("path")
            )
        }
        return profileFromOutbound(outbound, subscriptionId)
    }

    private fun parseTrojanUri(link: String, subscriptionId: String): ProxyProfile {
        val uri = URI(link)
        val password = decode(uri.rawUserInfo).takeIf(String::isNotBlank) ?: error("Missing password")
        val query = parseQuery(uri.rawQuery)
        val outbound = commonUriOutbound("trojan", uri, query) {
            put("password", password)
            addTlsAndTransport(query, uri, tlsDefault = true)
        }
        return profileFromOutbound(outbound, subscriptionId)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseShadowsocksUri(link: String, subscriptionId: String): ProxyProfile {
        val raw = link.substringAfter("ss://").substringBefore('#').substringBefore('?')
        val uri = runCatching { URI(link) }.getOrNull()
        val parsed = if (raw.contains('@')) {
            val userInfo = raw.substringBeforeLast('@')
            val credentials = decodeBase64(userInfo) ?: decode(userInfo)
            val endpoint = raw.substringAfterLast('@')
            Triple(credentials, endpoint.substringBeforeLast(':'), endpoint.substringAfterLast(':').toInt())
        } else {
            val decoded = decodeBase64(raw) ?: error("Invalid Shadowsocks payload")
            val credentials = decoded.substringBeforeLast('@')
            val endpoint = decoded.substringAfterLast('@')
            Triple(credentials, endpoint.substringBeforeLast(':'), endpoint.substringAfterLast(':').toInt())
        }
        val method = parsed.first.substringBefore(':')
        val password = parsed.first.substringAfter(':')
        require(method.isNotBlank() && password.isNotBlank()) { "Invalid Shadowsocks credentials" }
        val outbound = buildJsonObject {
            put("type", "shadowsocks")
            put("tag", decode(uri?.rawFragment).ifBlank { parsed.second })
            put("server", parsed.second.removeSurrounding("[", "]"))
            put("server_port", parsed.third)
            put("method", method)
            put("password", password)
            parseQuery(uri?.rawQuery)["plugin"]?.let { plugin ->
                put("plugin", plugin.substringBefore(';'))
                plugin.substringAfter(';', "").takeIf(String::isNotBlank)?.let { put("plugin_opts", it) }
            }
        }
        return profileFromOutbound(outbound, subscriptionId)
    }

    private fun parseHysteria2Uri(link: String, subscriptionId: String): ProxyProfile {
        val uri = URI(link.replaceFirst("hy2://", "hysteria2://", ignoreCase = true))
        val query = parseQuery(uri.rawQuery)
        val outbound = commonUriOutbound("hysteria2", uri, query) {
            put("password", decode(uri.rawUserInfo))
            query["obfs"]?.takeIf(String::isNotBlank)?.let { obfs ->
                putJsonObject("obfs") {
                    put("type", obfs)
                    query["obfs-password"]?.let { put("password", it) }
                }
            }
            addTlsAndTransport(query, uri, tlsDefault = true, includeTransport = false)
        }
        return profileFromOutbound(outbound, subscriptionId)
    }

    private fun parseTuicUri(link: String, subscriptionId: String): ProxyProfile {
        val uri = URI(link)
        val credentials = decode(uri.rawUserInfo).split(':', limit = 2)
        require(credentials.size == 2) { "Invalid TUIC credentials" }
        UUID.fromString(credentials[0])
        val query = parseQuery(uri.rawQuery)
        val outbound = commonUriOutbound("tuic", uri, query) {
            put("uuid", credentials[0].lowercase())
            put("password", credentials[1])
            query["congestion_control"]?.let { put("congestion_control", it) }
            query["udp_relay_mode"]?.let { put("udp_relay_mode", it) }
            addTlsAndTransport(query, uri, tlsDefault = true, includeTransport = false)
        }
        return profileFromOutbound(outbound, subscriptionId)
    }

    private fun commonUriOutbound(
        type: String,
        uri: URI,
        query: Map<String, String>,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit
    ): JsonObject {
        val server = uri.host ?: parseBracketedHost(uri.rawAuthority) ?: error("Missing server")
        val port = uri.port.takeIf { it in 1..65535 } ?: error("Invalid port")
        return buildJsonObject {
            put("type", type)
            put("tag", decode(uri.rawFragment).ifBlank { server })
            put("server", server)
            put("server_port", port)
            extra()
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.addTlsAndTransport(
        query: Map<String, String>,
        uri: URI,
        tlsDefault: Boolean = false,
        includeTransport: Boolean = true
    ) {
        addTls(
            security = query["security"] ?: if (tlsDefault) "tls" else null,
            serverName = query["sni"] ?: query["peer"],
            alpn = query["alpn"],
            fingerprint = query["fp"],
            realityPublicKey = query["pbk"],
            realityShortId = query["sid"],
            insecure = query["insecure"] ?: query["allowInsecure"]
        )
        if (includeTransport) {
            addTransport(
                type = query["type"].orEmpty(),
                path = query["path"],
                host = query["host"],
                serviceName = query["serviceName"] ?: query["service_name"]
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.addTls(
        security: String?,
        serverName: String?,
        alpn: String?,
        fingerprint: String?,
        realityPublicKey: String? = null,
        realityShortId: String? = null,
        insecure: String? = null
    ) {
        val normalized = security?.lowercase().orEmpty()
        if (normalized !in setOf("tls", "reality", "1", "true")) return
        if (normalized == "reality") {
            require(!serverName.isNullOrBlank()) { "REALITY server name is required" }
        }
        putJsonObject("tls") {
            put("enabled", true)
            serverName?.takeIf(String::isNotBlank)?.let { put("server_name", it) }
            alpn?.split(',')?.filter(String::isNotBlank)?.let { values ->
                put("alpn", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
            }
            if (insecure.equals("1") || insecure.equals("true", ignoreCase = true)) {
                put("insecure", true)
            }
            fingerprint?.takeIf(String::isNotBlank)?.let {
                putJsonObject("utls") {
                    put("enabled", true)
                    put("fingerprint", it)
                }
            }
            if (normalized == "reality") {
                require(!realityPublicKey.isNullOrBlank()) { "REALITY public key is required" }
                putJsonObject("reality") {
                    put("enabled", true)
                    put("public_key", realityPublicKey)
                    realityShortId?.takeIf(String::isNotBlank)?.let { put("short_id", it) }
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.addTransport(
        type: String,
        path: String?,
        host: String?,
        serviceName: String?
    ) {
        val normalized = type.lowercase()
        when (normalized) {
            "", "tcp", "raw" -> Unit
            "ws", "websocket" -> putJsonObject("transport") {
                put("type", "ws")
                path?.takeIf(String::isNotBlank)?.let { put("path", it) }
                host?.takeIf(String::isNotBlank)?.let {
                    putJsonObject("headers") { put("Host", it) }
                }
            }
            "grpc" -> putJsonObject("transport") {
                put("type", "grpc")
                serviceName?.takeIf(String::isNotBlank)?.let { put("service_name", it) }
            }
            "httpupgrade" -> putJsonObject("transport") {
                put("type", "httpupgrade")
                path?.takeIf(String::isNotBlank)?.let { put("path", it) }
                host?.takeIf(String::isNotBlank)?.let { put("host", it) }
            }
            else -> throw IllegalArgumentException("Unsupported transport: $type")
        }
    }

    private fun parseClashYaml(content: String, subscriptionId: String): ImportResult {
        val root = runCatching { yaml.loadFromString(content) as? Map<*, *> }.getOrNull()
            ?: throw UnsupportedSubscriptionFormatException("The subscription contains invalid YAML")
        val proxies = root["proxies"] as? List<*>
            ?: throw UnsupportedSubscriptionFormatException("The Clash subscription contains no proxies")
        val entries = proxies.mapNotNull { value ->
            val proxy = value as? Map<*, *> ?: return@mapNotNull null
            clashOutbound(proxy)
        }
        return parseOutboundEntries(entries, subscriptionId)
    }

    private fun clashOutbound(proxy: Map<*, *>): JsonObject {
        val type = proxy.text("type").lowercase().let {
            when (it) {
                "ss" -> "shadowsocks"
                "hy2" -> "hysteria2"
                else -> it
            }
        }
        return buildJsonObject {
            put("type", type)
            put("tag", proxy.text("name"))
            put("server", proxy.text("server"))
            put("server_port", proxy.int("port"))
            when (type) {
                "vless", "vmess" -> {
                    put("uuid", proxy.text("uuid"))
                    proxy.optionalInt("alterId")?.let { put("alter_id", it) }
                    proxy.optionalText("cipher")?.let { put("security", it) }
                    proxy.optionalText("flow")?.let { put("flow", it) }
                }
                "trojan" -> put("password", proxy.text("password"))
                "hysteria2" -> {
                    put("password", proxy.optionalText("password") ?: proxy.text("auth"))
                    proxy.optionalText("obfs")?.let { obfs ->
                        putJsonObject("obfs") {
                            put("type", obfs)
                            proxy.optionalText("obfs-password")?.let { put("password", it) }
                        }
                    }
                }
                "shadowsocks" -> {
                    put("method", proxy.text("cipher"))
                    put("password", proxy.text("password"))
                    proxy.optionalText("plugin")?.let { put("plugin", it) }
                    proxy.optionalText("plugin-opts")?.let { put("plugin_opts", it) }
                }
                "tuic" -> {
                    put("uuid", proxy.text("uuid"))
                    put("password", proxy.optionalText("password") ?: proxy.text("token"))
                    proxy.optionalText("congestion-controller")?.let { put("congestion_control", it) }
                    proxy.optionalText("udp-relay-mode")?.let { put("udp_relay_mode", it) }
                }
            }
            val realityOptions = proxy["reality-opts"] as? Map<*, *>
            val tlsEnabled = proxy.bool("tls") || realityOptions != null ||
                type in setOf("trojan", "hysteria2", "tuic")
            if (tlsEnabled) {
                putJsonObject("tls") {
                    put("enabled", true)
                    (proxy.optionalText("servername") ?: proxy.optionalText("sni"))
                        ?.let { put("server_name", it) }
                    if (proxy.bool("skip-cert-verify")) put("insecure", true)
                    proxy.optionalText("client-fingerprint")?.let { fingerprint ->
                        putJsonObject("utls") {
                            put("enabled", true)
                            put("fingerprint", fingerprint)
                        }
                    }
                    realityOptions?.let { reality ->
                        putJsonObject("reality") {
                            put("enabled", true)
                            put("public_key", reality.text("public-key"))
                            reality.optionalText("short-id")?.let { put("short_id", it) }
                        }
                    }
                }
            }
            val network = proxy.optionalText("network").orEmpty()
            val ws = proxy["ws-opts"] as? Map<*, *>
            val grpc = proxy["grpc-opts"] as? Map<*, *>
            addTransport(
                type = network,
                path = ws?.optionalText("path"),
                host = (ws?.get("headers") as? Map<*, *>)?.optionalText("Host"),
                serviceName = grpc?.optionalText("grpc-service-name")
            )
        }
    }

    private fun sip008Outbound(server: JsonObject): JsonObject = buildJsonObject {
        put("type", "shadowsocks")
        put("tag", server.string("remarks") ?: server.string("name") ?: server.string("server").orEmpty())
        put("server", server.string("server").orEmpty())
        put("server_port", server.flexibleInt("server_port") ?: server.flexibleInt("port") ?: 0)
        put("method", server.string("method").orEmpty())
        put("password", server.string("password").orEmpty())
        server.string("plugin")?.let { put("plugin", it) }
        server.string("plugin_opts")?.let { put("plugin_opts", it) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBody(content: String): String {
        var current = content.trim().removePrefix("\uFEFF")
        repeat(MAX_BASE64_DEPTH) {
            if (looksLikeStructuredContent(current)) return current
            val decoded = decodeBase64(current.filterNot(Char::isWhitespace)) ?: return current
            if (!looksLikeStructuredContent(decoded)) return current
            current = decoded.trim().removePrefix("\uFEFF")
        }
        return current
    }

    private fun looksLikeStructuredContent(value: String): Boolean {
        val trimmed = value.trimStart()
        return trimmed.startsWith('{') || trimmed.startsWith('[') ||
            looksLikeClashYaml(trimmed) ||
            trimmed.lineSequence().any { line ->
                SUPPORTED_SCHEMES.any { scheme -> line.trim().startsWith("$scheme://", true) }
            }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String): String? {
        val compact = value.filterNot(Char::isWhitespace)
        if (compact.isBlank()) return null
        val padding = "=".repeat((4 - compact.length % 4) % 4)
        return sequenceOf(Base64.Default, Base64.UrlSafe).mapNotNull { decoder ->
            runCatching {
                decoder.decode(compact + padding).decodeToString(throwOnInvalidSequence = true)
            }.getOrNull()
        }.firstOrNull()
    }

    private fun protocolFor(type: String): ProxyProtocol = when (type) {
        "vless" -> ProxyProtocol.VLESS
        "vmess" -> ProxyProtocol.VMESS
        "trojan" -> ProxyProtocol.TROJAN
        "shadowsocks" -> ProxyProtocol.SHADOWSOCKS
        "hysteria2" -> ProxyProtocol.HYSTERIA2
        "tuic" -> ProxyProtocol.TUIC
        else -> error("Unsupported protocol: $type")
    }

    private fun requireRealProfile(profile: ProxyProfile) {
        val normalizedName = profile.name.lowercase().replace('ё', 'е')
        val unsupportedName = listOf(
            "приложение не поддерживается",
            "приложение не поддерживает",
            "application is not supported",
            "app is not supported",
            "unsupported application",
            "unsupported client"
        ).any(normalizedName::contains)
        if (unsupportedName) throw UnsupportedSubscriptionAppException()
        val deviceLimitName = listOf(
            "лимит девайсов",
            "лимит устройств",
            "удалите устройство",
            "обратитесь в поддержку"
        ).any(normalizedName::contains)
        if (deviceLimitName) {
            throw SubscriptionDeviceLimitException(
                "The subscription device limit has been reached"
            )
        }
    }

    private fun looksLikeHtml(value: String): Boolean {
        val trimmed = value.trimStart().lowercase()
        return trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html") ||
            ("<head" in trimmed && "<body" in trimmed)
    }

    private fun looksLikeClashYaml(value: String): Boolean =
        value.lineSequence().take(30).any { it.trimStart().startsWith("proxies:") }

    private fun parseQuery(rawQuery: String?): Map<String, String> = rawQuery.orEmpty()
        .split('&')
        .filter(String::isNotBlank)
        .associate { part ->
            decode(part.substringBefore('=')) to decode(part.substringAfter('=', ""))
        }

    private fun parseBracketedHost(authority: String?): String? = authority
        ?.substringAfter('@')
        ?.takeIf { it.startsWith('[') }
        ?.substringAfter('[')
        ?.substringBefore(']')

    private fun decode(value: String?): String =
        URLDecoder.decode(value.orEmpty(), StandardCharsets.UTF_8.name())

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? =
        (get(name) as? JsonPrimitive)?.intOrNull

    private fun JsonObject.flexibleInt(name: String): Int? =
        (get(name) as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun JsonObject.boolean(name: String): Boolean? =
        (get(name) as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.stringList(name: String): List<String>? = when (val value = get(name)) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> value.contentOrNull?.split(',')?.map(String::trim)
        else -> null
    }

    private fun JsonObject.stringIgnoreCase(name: String): String? = entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull

    private fun Map<*, *>.text(name: String): String =
        optionalText(name)?.takeIf(String::isNotBlank) ?: error("Missing $name")

    private fun Map<*, *>.optionalText(name: String): String? = entries
        .firstOrNull { it.key?.toString()?.equals(name, ignoreCase = true) == true }
        ?.value
        ?.toString()

    private fun Map<*, *>.int(name: String): Int =
        optionalText(name)?.toIntOrNull() ?: error("Invalid $name")

    private fun Map<*, *>.optionalInt(name: String): Int? = optionalText(name)?.toIntOrNull()

    private fun Map<*, *>.bool(name: String): Boolean = when (optionalText(name)?.lowercase()) {
        "true", "1", "yes", "on" -> true
        else -> false
    }

    private companion object {
        const val MAX_SUBSCRIPTION_LENGTH = 5 * 1024 * 1024
        const val MAX_BASE64_DEPTH = 2
        val SUPPORTED_SCHEMES = setOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "tuic")
        val SUPPORTED_OUTBOUND_TYPES = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic")
        val IGNORED_OUTBOUND_TYPES = setOf("direct", "block", "dns", "selector", "urltest")
        val PROVIDER_REFERENCE_FIELDS = setOf("tag", "detour", "domain_resolver")
        val SUPPORTED_TRANSPORT_TYPES = setOf("", "tcp", "raw", "ws", "websocket", "grpc", "httpupgrade")
    }

    private class UnsupportedTransportException(val transport: String) :
        IllegalArgumentException("Unsupported transport: $transport")
}

typealias VlessProfileParser = UniversalProfileParser
