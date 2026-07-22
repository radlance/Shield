package com.github.radlance.shield.home.presentation

data class ServerGroup(
    val title: String,
    val items: List<MockServerItem>,
    val onRefresh: (() -> Unit)? = null,
    val isRefreshing: Boolean = false
)
