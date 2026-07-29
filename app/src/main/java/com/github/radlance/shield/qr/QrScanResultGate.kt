package com.github.radlance.shield.qr

import java.util.concurrent.atomic.AtomicBoolean

internal class QrScanResultGate {
    private val delivered = AtomicBoolean(false)

    fun tryDeliver(value: String?, onResult: (String) -> Unit): Boolean {
        if (value.isNullOrBlank() || !delivered.compareAndSet(false, true)) {
            return false
        }
        onResult(value)
        return true
    }
}
