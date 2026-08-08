package com.github.radlance.shield.subscription.domain

interface ProfileParser {
    fun parseSubscription(content: String, subscriptionId: String): ImportResult
    fun parseLink(link: String, subscriptionId: String): ProxyProfile
    fun parseVless(link: String, subscriptionId: String): ProxyProfile =
        parseLink(link, subscriptionId)
}
