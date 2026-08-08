package com.github.radlance.shield.vpn.domain

import com.github.radlance.shield.subscription.domain.ProxyProfile

fun interface ServerLatencyTester {
    suspend fun measure(profile: ProxyProfile): Long?
}
