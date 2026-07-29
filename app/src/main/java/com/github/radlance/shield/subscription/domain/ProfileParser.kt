package com.github.radlance.shield.subscription.domain

interface ProfileParser {
    fun parseSubscription(content: String, subscriptionId: String): ImportResult
    fun parseVless(link: String, subscriptionId: String): VlessProfile
}
