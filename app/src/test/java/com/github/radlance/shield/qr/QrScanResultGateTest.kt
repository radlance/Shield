package com.github.radlance.shield.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScanResultGateTest {
    @Test
    fun ignoresEmptyValues() {
        val gate = QrScanResultGate()

        assertFalse(gate.tryDeliver(null) { error("Must not be called") })
        assertFalse(gate.tryDeliver("  ") { error("Must not be called") })
    }

    @Test
    fun deliversOnlyFirstValue() {
        val gate = QrScanResultGate()
        val delivered = mutableListOf<String>()

        assertTrue(gate.tryDeliver("vless://first") { delivered += it })
        assertFalse(gate.tryDeliver("vless://second") { delivered += it })

        assertEquals(listOf("vless://first"), delivered)
    }
}
