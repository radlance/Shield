package com.github.radlance.shield.home.presentation

data class ServerItem(
    val id: String,
    val leadingIcon: String,
    val title: String,
    val description: String?,
    val latency: ServerLatency = ServerLatency.Idle
)

sealed interface ServerLatency {
    data object Idle : ServerLatency
    data object Pinging : ServerLatency
    data class Available(val milliseconds: Long) : ServerLatency
    data object Unavailable : ServerLatency
}
