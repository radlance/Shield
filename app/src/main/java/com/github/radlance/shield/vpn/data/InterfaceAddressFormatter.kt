package com.github.radlance.shield.vpn.data

internal object InterfaceAddressFormatter {
    fun format(hostAddress: String, prefixLength: Int): String {
        val addressWithoutZone = if (':' in hostAddress) {
            hostAddress.substringBefore('%')
        } else {
            hostAddress
        }
        return "$addressWithoutZone/$prefixLength"
    }
}
