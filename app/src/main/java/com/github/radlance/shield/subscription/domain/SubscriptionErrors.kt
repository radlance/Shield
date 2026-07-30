package com.github.radlance.shield.subscription.domain

class UnsupportedSubscriptionAppException(
    message: String = "The subscription provider does not support this application"
) : IllegalArgumentException(message)

class UnsupportedSubscriptionFormatException(
    message: String
) : IllegalArgumentException(message)

class SubscriptionDeviceLimitException(
    message: String
) : IllegalStateException(message)
