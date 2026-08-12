package com.github.radlance.shield.subscription.data

import com.github.radlance.shield.subscription.domain.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PinnedSubscriptionOrderTest {
    @Test
    fun reordersPinnedSubscriptionsAndNormalizesTheirValues() {
        val subscriptions = listOf(
            subscription("first", pinOrder = 10),
            subscription("unpinned"),
            subscription("second", pinOrder = 20)
        )

        val reordered = subscriptions.withPinnedOrder(listOf("second", "first"))

        assertEquals(1L, reordered.first { it.id == "first" }.pinOrder)
        assertEquals(0L, reordered.first { it.id == "second" }.pinOrder)
        assertNull(reordered.first { it.id == "unpinned" }.pinOrder)
        assertEquals(subscriptions.map { it.id }, reordered.map { it.id })
    }

    @Test
    fun rejectsDuplicateIds() {
        val subscriptions = listOf(
            subscription("first", pinOrder = 0),
            subscription("second", pinOrder = 1)
        )

        assertThrows(IllegalArgumentException::class.java) {
            subscriptions.withPinnedOrder(listOf("first", "first"))
        }
    }

    @Test
    fun rejectsMissingOrUnpinnedIds() {
        val subscriptions = listOf(
            subscription("first", pinOrder = 0),
            subscription("second", pinOrder = 1),
            subscription("unpinned")
        )

        assertThrows(IllegalArgumentException::class.java) {
            subscriptions.withPinnedOrder(listOf("first"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            subscriptions.withPinnedOrder(listOf("first", "unpinned"))
        }
    }

    private fun subscription(id: String, pinOrder: Long? = null) = Subscription(
        id = id,
        name = id,
        createdAtEpochMillis = 0,
        pinOrder = pinOrder
    )
}
