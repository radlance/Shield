package com.github.radlance.shield.subscription.debug

import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.subscription.domain.SubscriptionSource
import kotlinx.coroutines.flow.first

internal suspend fun seedDebugSubscriptions(repository: SubscriptionRepository) {
    val existingNames = repository.groups.first().mapTo(mutableSetOf()) {
        it.subscription.name
    }

    DEBUG_SUBSCRIPTIONS.forEach { fixture ->
        if (fixture.name in existingNames) return@forEach

        repository.import(
            name = fixture.name,
            source = SubscriptionSource.Direct(fixture.link)
        ).onSuccess { subscription ->
            if (fixture.pinned) repository.setPinned(subscription.id, pinned = true)
        }
    }
}

private data class DebugSubscription(
    val name: String,
    val link: String,
    val pinned: Boolean
)

private val DEBUG_SUBSCRIPTIONS = listOf(
    DebugSubscription(
        name = "Open Source Alpha",
        link = "vless://11111111-1111-4111-8111-111111111111@alpha.fixture.invalid:443" +
            "?type=ws&security=tls&sni=alpha.fixture.invalid&path=%2Fshield-fixture" +
            "&host=alpha.fixture.invalid#Amsterdam",
        pinned = true
    ),
    DebugSubscription(
        name = "Community Bravo",
        link = "trojan://fixture-only@bravo.fixture.invalid:443" +
            "?security=tls&sni=bravo.fixture.invalid#Frankfurt",
        pinned = true
    ),
    DebugSubscription(
        name = "Libre Charlie",
        link = "ss://YWVzLTEyOC1nY206Zml4dHVyZS1vbmx5@charlie.fixture.invalid:8388#Helsinki",
        pinned = true
    ),
    DebugSubscription(
        name = "FOSS Delta",
        link = "hysteria2://fixture-only@delta.fixture.invalid:443" +
            "?sni=delta.fixture.invalid#Singapore",
        pinned = false
    )
)
