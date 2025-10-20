package com.rebuildit.prestaflow.domain.notifications

import java.time.LocalTime

data class NotificationSettings(
    val notificationsEnabled: Boolean = true,
    val topics: List<String> = emptyList(),
    val deviceToken: String? = null,
    val lastSyncedToken: String? = null,
    val doNotDisturb: DoNotDisturb = DoNotDisturb()
) {
    val isTokenSynced: Boolean
        get() = deviceToken != null && deviceToken == lastSyncedToken
}

data class DoNotDisturb(
    val enabled: Boolean = false,
    val start: LocalTime? = null,
    val end: LocalTime? = null
)
