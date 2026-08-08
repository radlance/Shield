package com.github.radlance.shield.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogTest {
    @Test
    fun redactsLinksUuidsAndTokens() {
        val uuid = "123e4567-e89b-42d3-a456-426614174000"
        val source = "failed vless://$uuid@example.com:443?token=secret id=$uuid"

        val redacted = DiagnosticLog().redact(source)

        assertFalse(redacted.contains(uuid))
        assertFalse(redacted.contains("secret"))
        assertTrue(redacted.contains("[redacted]"))
    }

    @Test
    fun clearRemovesEntriesAndExport() {
        val log = DiagnosticLog()
        log.record("first")
        log.record("second")

        log.clear()

        assertTrue(log.lines.value.isEmpty())
        assertTrue(log.export().isEmpty())
    }
}
