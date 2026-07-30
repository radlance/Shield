package com.github.radlance.shield.subscription.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.github.radlance.shield.BuildConfig
import com.github.radlance.shield.subscription.domain.SubscriptionDeviceLimitException
import com.github.radlance.shield.subscription.domain.SubscriptionMetadata
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DownloadedSubscription(
    val body: String,
    val contentType: String?,
    val headers: Map<String, String>
)

interface SubscriptionDownloader {
    suspend fun download(url: String): DownloadedSubscription
}

internal fun DownloadedSubscription.validatedBody(): String {
    if (headers.isEnabled("x-hwid-max-devices-reached") || headers.isEnabled("x-hwid-limit")) {
        throw SubscriptionDeviceLimitException(
            "The subscription device limit has been reached"
        )
    }
    if (headers.isEnabled("x-hwid-not-supported")) {
        throw SubscriptionDeviceLimitException(
            "The subscription provider did not accept this device identifier"
        )
    }
    return body
}

@OptIn(ExperimentalEncodingApi::class)
internal fun DownloadedSubscription.profileTitle(): String? {
    return metadataValue("profile-title")?.decodeMetadataText()
}

internal fun DownloadedSubscription.metadata(): SubscriptionMetadata {
    val userInfo = metadataValue("subscription-userinfo")
        ?.split(';')
        ?.mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            part.substring(0, separator).trim().lowercase(Locale.ROOT) to
                part.substring(separator + 1).trim()
        }
        ?.toMap()
        .orEmpty()

    return SubscriptionMetadata(
        uploadBytes = userInfo.nonNegativeLong("upload"),
        downloadBytes = userInfo.nonNegativeLong("download"),
        totalBytes = userInfo.nonNegativeLong("total"),
        expiresAtEpochSeconds = userInfo.nonNegativeLong("expire")
            ?.takeIf { it <= Long.MAX_VALUE / 1_000 },
        announcement = metadataValue("announce")
            ?.decodeMetadataText()
            ?.take(MAX_ANNOUNCEMENT_LENGTH),
        supportUrl = metadataValue("support-url")?.safeExternalUrl(),
        webPageUrl = metadataValue("profile-web-page-url")?.safeExternalUrl(),
        updateIntervalHours = metadataValue("profile-update-interval")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
    )
}

private fun DownloadedSubscription.metadataValue(name: String): String? {
    val headerValue = headers.entries
        .firstOrNull { (headerName, _) -> headerName.equals(name, ignoreCase = true) }
        ?.value
    val bodyValue = body.lineSequence()
        .take(10)
        .map(String::trim)
        .mapNotNull { line ->
            val content = when {
                line.startsWith("//") -> line.substring(2)
                line.startsWith("#") -> line.substring(1)
                else -> return@mapNotNull null
            }.trim()
            val separator = content.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val fieldName = content.substring(0, separator).trim()
            if (!fieldName.equals(name, ignoreCase = true)) return@mapNotNull null
            content.substring(separator + 1)
        }
        .firstOrNull()
    return (headerValue ?: bodyValue)
        ?.trim()
        ?.takeUnless { it.isBlank() || it.equals("NULL", ignoreCase = true) }
}

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeMetadataText(): String? {
    if (!startsWith("base64:", ignoreCase = true)) return trim().takeIf(String::isNotBlank)
    val encoded = substringAfter(':').trim()
    if (encoded.isBlank()) return null
    val padding = "=".repeat((4 - encoded.length % 4) % 4)
    return sequenceOf(Base64.Default, Base64.UrlSafe)
        .mapNotNull { decoder ->
            runCatching {
                decoder.decode(encoded + padding)
                    .decodeToString(throwOnInvalidSequence = true)
                    .trim()
            }.getOrNull()
        }
        .firstOrNull(String::isNotBlank)
}

private fun Map<String, String>.nonNegativeLong(name: String): Long? =
    get(name)?.toLongOrNull()?.takeIf { it >= 0 }

private fun String.safeExternalUrl(): String? = runCatching {
    val value = trim()
    val uri = URI(value)
    when (uri.scheme?.lowercase(Locale.ROOT)) {
        "http", "https" -> value.takeIf { !uri.host.isNullOrBlank() }
        "tg" -> value.takeIf { uri.schemeSpecificPart.isNotBlank() }
        else -> null
    }
}.getOrNull()

class AndroidSubscriptionDownloader(
    context: Context
) : SubscriptionDownloader {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun download(url: String): DownloadedSubscription =
        withContext(Dispatchers.IO) {
            validateSubscriptionUrl(url)
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("User-Agent", compatibleUserAgent())
                connection.setRequestProperty("X-Client-Name", "Shield")
                connection.setRequestProperty("X-Hwid", installationId())
                connection.setRequestProperty("X-Device-OS", "Android")
                connection.setRequestProperty("X-Ver-OS", Build.VERSION.RELEASE.orEmpty())
                connection.setRequestProperty("X-Device-Model", Build.MODEL.orEmpty())
                connection.setRequestProperty(
                    "Accept",
                    "application/json, text/plain, application/octet-stream"
                )

                val status = connection.responseCode
                require(connection.url.protocol.equals("https", ignoreCase = true)) {
                    "Subscription redirect must remain on HTTPS"
                }
                require(status in 200..299) {
                    "Subscription server returned HTTP $status"
                }
                DownloadedSubscription(
                    body = connection.inputStream.bufferedReader().use { it.readText() },
                    contentType = connection.contentType,
                    headers = connection.headerFields
                        .filterKeys { it != null }
                        .mapKeys { (name, _) -> name!!.lowercase(Locale.ROOT) }
                        .mapValues { (_, values) -> values.orEmpty().joinToString(",") }
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun installationId(): String = synchronized(preferences) {
        preferences.getString(INSTALLATION_ID_KEY, null)
            ?: UUID.randomUUID().toString().also { generated ->
                preferences.edit { putString(INSTALLATION_ID_KEY, generated) }
            }
    }

    private fun compatibleUserAgent(): String =
        "SFA/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}; " +
            "sing-box $SING_BOX_VERSION; Shield; language ${Locale.getDefault().toLanguageTag()})"

    private fun validateSubscriptionUrl(url: String) {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Subscription URL must use HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "Subscription URL has no host" }
    }

    private companion object {
        const val PREFERENCES_NAME = "subscription_client"
        const val INSTALLATION_ID_KEY = "installation_id"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val SING_BOX_VERSION = "1.13.12"
    }
}

private fun Map<String, String>.isEnabled(name: String): Boolean =
    get(name)?.trim()?.equals("true", ignoreCase = true) == true

private const val MAX_ANNOUNCEMENT_LENGTH = 4_096
