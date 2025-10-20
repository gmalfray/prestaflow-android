package com.rebuildit.prestaflow.domain.notifications

import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

interface NotificationsRepository {
    val settings: Flow<NotificationSettings>

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun updateTopics(topics: List<String>)

    suspend fun updateDoNotDisturb(enabled: Boolean, start: LocalTime?, end: LocalTime?)

    suspend fun syncRegistration(token: String, deviceId: String?)

    suspend fun clearRegistration()
}
