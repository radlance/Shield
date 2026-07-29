package com.github.radlance.shield.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceAddressFormatterTest {

    @Test
    fun `removes interface name from scoped IPv6 address`() {
        assertEquals(
            "fe80::6ce5:26ff:fe4c:9803/64",
            InterfaceAddressFormatter.format("fe80::6ce5:26ff:fe4c:9803%dummy0", 64)
        )
    }

    @Test
    fun `removes numeric zone from scoped IPv6 address`() {
        assertEquals(
            "fe80::1/64",
            InterfaceAddressFormatter.format("fe80::1%16", 64)
        )
    }

    @Test
    fun `keeps unscoped addresses unchanged`() {
        assertEquals(
            "192.0.2.1/24",
            InterfaceAddressFormatter.format("192.0.2.1", 24)
        )
        assertEquals(
            "2001:db8::1/64",
            InterfaceAddressFormatter.format("2001:db8::1", 64)
        )
    }
}
