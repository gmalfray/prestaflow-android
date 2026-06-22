package com.rebuildit.prestaflow.domain.notifications

import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface NotificationsRepository {
    val settings: Flow<NotificationSettings>

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun updateTopics(topics: List<String>)

    suspend fun updateDoNotDisturb(
        enabled: Boolean,
        start: LocalTime?,
        end: LocalTime?,
    )

    suspend fun syncRegistration(
        token: String,
        deviceId: String?,
    )

    /**
     * Marque l'enregistrement comme « non synchronisé » (garde le token FCM mais oublie
     * qu'il a été enregistré) → force un réenregistrement sur la boutique active.
     * Utilisé lors d'une bascule de boutique (multi-boutiques).
     */
    suspend fun markRegistrationStale()

    suspend fun clearRegistration()
}
