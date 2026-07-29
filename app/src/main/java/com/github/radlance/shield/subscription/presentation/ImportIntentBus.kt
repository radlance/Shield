package com.github.radlance.shield.subscription.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ImportIntentBus {
    private val _values = MutableStateFlow<String?>(null)
    val values = _values.asStateFlow()

    fun offer(value: String) {
        if (value.isNotBlank()) _values.value = value
    }

    fun consume() {
        _values.value = null
    }
}
