package com.github.radlance.shield.home.presentation

data class ServerGroup(
    val id: String,
    val title: String,
    val items: List<ServerItem>,
    val onRefresh: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null
)
