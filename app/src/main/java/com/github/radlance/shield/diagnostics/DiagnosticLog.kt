package com.github.radlance.shield.diagnostics

import java.text.DateFormat
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticLog {
    private val entries = CopyOnWriteArrayList<String>()
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun record(message: String) {
        val safe = redact(message)
        entries += "${DateFormat.getDateTimeInstance().format(Date())}  $safe"
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        _lines.value = entries.toList()
    }

    fun export(): String = entries.joinToString("\n")

    fun clear() {
        entries.clear()
        _lines.value = emptyList()
    }

    internal fun redact(value: String): String = value
        .replace(PROXY_LINK, "$1://[redacted]")
        .replace(UUID_VALUE, "[uuid]")
        .replace(TOKEN_QUERY, "$1=[redacted]")

    private companion object {
        const val MAX_ENTRIES = 300
        val PROXY_LINK = Regex(
            """(vless|vmess|trojan|ss|hysteria2|hy2|tuic)://\S+""",
            RegexOption.IGNORE_CASE
        )
        val UUID_VALUE = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b""")
        val TOKEN_QUERY = Regex("""(?i)(token|key|auth|uuid)=([^&\s]+)""")
    }
}
