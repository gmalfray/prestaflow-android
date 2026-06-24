package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.notifications.NotificationSettings
import com.rebuildit.prestaflow.domain.notifications.NotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime

/**
 * Fake de [NotificationsRepository] pour les tests unitaires.
 *
 * Expose des compteurs d'appels pour vérifier les interactions
 * sans effets de bord réseau ni DataStore réel.
 */
class FakeNotificationsRepository : NotificationsRepository {

    private val _settings = MutableStateFlow(NotificationSettings())
    override val settings: Flow<NotificationSettings> = _settings

    var syncRegistrationCallCount: Int = 0
        private set

    var markStaleCallCount: Int = 0
        private set

    var clearRegistrationCallCount: Int = 0
        private set

    var lastSyncedToken: String? = null
        private set

    fun setSettings(settings: NotificationSettings) {
        _settings.value = settings
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun updateTopics(topics: List<String>) = Unit

    override suspend fun updateDoNotDisturb(
        enabled: Boolean,
        start: LocalTime?,
        end: LocalTime?,
    ) = Unit

    override suspend fun syncRegistration(
        token: String,
        deviceId: String?,
    ) {
        syncRegistrationCallCount++
        lastSyncedToken = token
        // Simule la mise à jour de l'état : le token est maintenant synchronisé.
        _settings.value = _settings.value.copy(
            deviceToken = token,
            lastSyncedToken = token,
        )
    }

    override suspend fun markRegistrationStale() {
        markStaleCallCount++
        // Simule ce que fait l'implémentation réelle : oublie lastSyncedToken.
        _settings.value = _settings.value.copy(lastSyncedToken = null)
    }

    override suspend fun clearRegistration() {
        clearRegistrationCallCount++
        _settings.value = _settings.value.copy(deviceToken = null, lastSyncedToken = null)
    }
}
