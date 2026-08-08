package com.github.radlance.shield.navigation.destination

import kotlinx.serialization.Serializable

interface AppDestination

@Serializable
object Main : AppDestination

@Serializable
object Appearance : AppDestination

@Serializable
object Diagnostics : AppDestination

@Serializable
object About : AppDestination
