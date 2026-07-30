package com.github.radlance.shield.vpn.routing

import android.content.Context
import com.github.radlance.shield.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class RoutingRuleSetPaths(
    val blockedDomains: String,
    val blockedIps: String,
    val blockedCommunityIps: String,
    val availableOnlyInsideDomains: String,
    val russianDomains: String,
    val russianIps: String
)

data class RuleSetRefreshResult(
    val updated: Boolean,
    val failedDownloads: Int
)

class RoutingRuleSetProvider(
    context: Context
) {
    private val applicationContext = context.applicationContext

    @Synchronized
    fun prepareRuleSets(): RoutingRuleSetPaths {
        val directory = ruleSetDirectory()
        RULE_SETS.forEach { ruleSet ->
            val destination = File(directory, ruleSet.fileName)
            if (!destination.isValidRuleSet()) {
                installBundledRuleSet(ruleSet, destination)
            }
        }
        return paths(directory)
    }

    @Synchronized
    fun refreshRuleSets(): RuleSetRefreshResult {
        val directory = ruleSetDirectory()
        val refreshMarker = File(directory, REFRESH_MARKER_FILE)
        val now = System.currentTimeMillis()
        if (
            refreshMarker.isFile &&
            now - refreshMarker.lastModified() < REFRESH_INTERVAL_MILLIS
        ) {
            return RuleSetRefreshResult(updated = false, failedDownloads = 0)
        }
        var changed = false
        var failedDownloads = 0
        RULE_SETS.forEach { ruleSet ->
            val destination = File(directory, ruleSet.fileName)
            val temporary = File(directory, "${ruleSet.fileName}.download")
            runCatching {
                download(ruleSet.url, temporary)
                check(temporary.isValidRuleSet()) {
                    "Downloaded ${ruleSet.fileName} is not a valid sing-box rule-set"
                }
                if (destination.isFile && destination.sha256() == temporary.sha256()) {
                    temporary.delete()
                } else {
                    replaceAtomically(temporary, destination)
                    changed = true
                }
            }.onFailure {
                temporary.delete()
                failedDownloads += 1
            }
        }
        if (failedDownloads == 0) {
            refreshMarker.createNewFile()
            refreshMarker.setLastModified(now)
        }
        return RuleSetRefreshResult(
            updated = changed,
            failedDownloads = failedDownloads
        )
    }

    private fun File.sha256(): String =
        inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    private fun installBundledRuleSet(ruleSet: RuleSet, destination: File) {
        val temporary = File(destination.parentFile, "${ruleSet.fileName}.bundled")
        applicationContext.resources.openRawResource(ruleSet.resourceId).use { input ->
            temporary.outputStream().use(input::copyTo)
        }
        check(temporary.sha256() == ruleSet.bundledSha256) {
            temporary.delete()
            "Bundled ${ruleSet.fileName} failed integrity verification"
        }
        replaceAtomically(temporary, destination)
    }

    private fun download(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = DOWNLOAD_TIMEOUT_MILLIS
        connection.readTimeout = DOWNLOAD_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Shield rule-set updater")
        try {
            check(connection.responseCode in 200..299) {
                "Rule-set download returned HTTP ${connection.responseCode}"
            }
            check(connection.contentLengthLong <= MAX_RULE_SET_BYTES) {
                "Rule-set is too large"
            }
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= MAX_RULE_SET_BYTES) { "Rule-set is too large" }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun replaceAtomically(source: File, destination: File) {
        val backup = File(destination.parentFile, "${destination.name}.previous")
        backup.delete()
        if (destination.exists()) {
            check(destination.renameTo(backup)) {
                "Unable to back up ${destination.name}"
            }
        }
        if (!source.renameTo(destination)) {
            backup.renameTo(destination)
            error("Unable to install ${destination.name}")
        }
        backup.delete()
    }

    private fun File.isValidRuleSet(): Boolean {
        if (!isFile || length() !in MIN_RULE_SET_BYTES..MAX_RULE_SET_BYTES) return false
        return inputStream().use { input ->
            input.read() == 'S'.code &&
                input.read() == 'R'.code &&
                input.read() == 'S'.code &&
                input.read() in 1..2
        }
    }

    private fun ruleSetDirectory() =
        File(applicationContext.filesDir, RULE_SET_DIRECTORY).apply {
            check(exists() || mkdirs()) { "Unable to create routing rule-set directory" }
        }

    private fun paths(directory: File) = RoutingRuleSetPaths(
        blockedDomains = File(directory, BLOCKED_DOMAINS.fileName).absolutePath,
        blockedIps = File(directory, BLOCKED_IPS.fileName).absolutePath,
        blockedCommunityIps = File(directory, BLOCKED_COMMUNITY_IPS.fileName).absolutePath,
        availableOnlyInsideDomains =
            File(directory, AVAILABLE_ONLY_INSIDE.fileName).absolutePath,
        russianDomains = File(directory, RUSSIAN_DOMAINS.fileName).absolutePath,
        russianIps = File(directory, RUSSIAN_IPS.fileName).absolutePath
    )

    private data class RuleSet(
        val resourceId: Int,
        val fileName: String,
        val bundledSha256: String,
        val url: String
    )

    private companion object {
        const val RULE_SET_DIRECTORY = "routing"
        const val REFRESH_MARKER_FILE = ".last_refresh"
        const val REFRESH_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val DOWNLOAD_TIMEOUT_MILLIS = 15_000
        const val MIN_RULE_SET_BYTES = 8L
        const val MAX_RULE_SET_BYTES = 16L * 1024 * 1024
        const val BASE_URL =
            "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/" +
                "release/sing-box"
        const val GEOSITE_URL = "$BASE_URL/rule-set-geosite"
        const val GEOIP_URL = "$BASE_URL/rule-set-geoip"

        val BLOCKED_DOMAINS = RuleSet(
            R.raw.geosite_ru_blocked,
            "geosite-ru-blocked.srs",
            "31e8b4bb7f360ffc6ec61bdec55e8bc4899f04e29c33019068ccc4e756982c55",
            "$GEOSITE_URL/geosite-ru-blocked.srs"
        )
        val BLOCKED_IPS = RuleSet(
            R.raw.geoip_ru_blocked,
            "geoip-ru-blocked.srs",
            "855a77db39d71a5c4c06d82b0c2006efd0c1cae73c494ece85934fbdadb05862",
            "$GEOIP_URL/geoip-ru-blocked.srs"
        )
        val BLOCKED_COMMUNITY_IPS = RuleSet(
            R.raw.geoip_ru_blocked_community,
            "geoip-ru-blocked-community.srs",
            "f8c04af482979ebdc07409261e1814d6b147b7b16c20944316e75caeba3c894f",
            "$GEOIP_URL/geoip-ru-blocked-community.srs"
        )
        val AVAILABLE_ONLY_INSIDE = RuleSet(
            R.raw.geosite_ru_available_only_inside,
            "geosite-ru-available-only-inside.srs",
            "d6ba57d47d190897a9f4445fd2d19e180de1d97322ba2ab60c16af6e05b49202",
            "$GEOSITE_URL/geosite-ru-available-only-inside.srs"
        )
        val RUSSIAN_DOMAINS = RuleSet(
            R.raw.geosite_category_ru,
            "geosite-category-ru.srs",
            "dd45406cf7531f9acdf5379f705dd80a4cf8c5ef7919ec85bb8ec60a408b5416",
            "$GEOSITE_URL/geosite-category-ru.srs"
        )
        val RUSSIAN_IPS = RuleSet(
            R.raw.geoip_ru,
            "geoip-ru.srs",
            "629660125b194ce7a6290c6fd95f08a6d5e45578397b7edda38690bc1eaa0b47",
            "$GEOIP_URL/geoip-ru.srs"
        )
        val RULE_SETS = listOf(
            BLOCKED_DOMAINS,
            BLOCKED_IPS,
            BLOCKED_COMMUNITY_IPS,
            AVAILABLE_ONLY_INSIDE,
            RUSSIAN_DOMAINS,
            RUSSIAN_IPS
        )
    }
}
