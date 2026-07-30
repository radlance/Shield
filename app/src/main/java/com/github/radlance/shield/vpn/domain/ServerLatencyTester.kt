package com.github.radlance.shield.vpn.domain

import com.github.radlance.shield.subscription.domain.VlessProfile

fun interface ServerLatencyTester {
    suspend fun measure(profile: VlessProfile): Long?
}
