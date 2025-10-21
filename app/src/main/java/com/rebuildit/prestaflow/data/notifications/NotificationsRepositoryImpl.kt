package com.rebuildit.prestaflow.data.notifications

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.DeviceRegistrationRequestDto
import com.rebuildit.prestaflow.data.remote.dto.DoNotDisturbDto
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.notifications.DoNotDisturb
import com.rebuildit.prestaflow.domain.notifications.NotificationSettings
import com.rebuildit.prestaflow.domain.notifications.NotificationsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class NotificationsRepositoryImpl @Inject constructor(
    private val api: PrestaFlowApi,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val networkErrorMapper: NetworkErrorMapper,
    private val ioDispatcher: CoroutineDispatcher
) : NotificationsRepository {

    override val settings: Flow<NotificationSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    Timber.w(error, "Failed to read notification preferences")
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { prefs -> prefs.toDomain() }
            .distinctUntilChanged()

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
            }
            val current = settings.first()
            val token = current.deviceToken
            when {
                !enabled && token != null && current.lastSyncedToken == token -> {
                    unregisterRemote(token)
                    dataStore.edit { prefs ->
                        prefs.remove(KEY_LAST_SYNCED_TOKEN)
                        prefs.remove(KEY_DEVICE_TOKEN)
                    }
                }
                enabled && token != null && !current.isTokenSynced -> {
                    syncRegistration(token, currentDeviceId())
                }
                else -> Unit
            }
        }
    }

    override suspend fun updateTopics(topics: List<String>) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[KEY_TOPICS] = serializeTopics(topics)
            }
            val current = settings.first()
            val token = current.deviceToken
            if (current.notificationsEnabled && token != null) {
                syncRegistration(token, currentDeviceId())
            }
        }
    }

    override suspend fun updateDoNotDisturb(
        enabled: Boolean,
        start: LocalTime?,
        end: LocalTime?
    ) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[KEY_DND_ENABLED] = enabled
                if (start != null) {
                    prefs[KEY_DND_START] = TIME_FORMATTER.format(start)
                } else {
                    prefs.remove(KEY_DND_START)
                }
                if (end != null) {
                    prefs[KEY_DND_END] = TIME_FORMATTER.format(end)
                } else {
                    prefs.remove(KEY_DND_END)
                }
            }
            val current = settings.first()
            val token = current.deviceToken
            if (current.notificationsEnabled && token != null) {
                syncRegistration(token, currentDeviceId())
            }
        }
    }

    override suspend fun syncRegistration(token: String, deviceId: String?) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[KEY_DEVICE_TOKEN] = token
            }

            val currentSettings = settings.first()
            if (!currentSettings.notificationsEnabled) {
                Timber.d("Notifications disabled, skipping remote registration")
                return@withContext
            }

            val activeToken = authRepository.getActiveToken()
            if (activeToken == null) {
                Timber.w("No active auth token, cannot register FCM device")
                return@withContext
            }

            val resolvedDeviceId = deviceId ?: currentDeviceId()
            val topics = currentSettings.topics
            val dnd = currentSettings.doNotDisturb.takeIf { it.enabled && it.start != null && it.end != null }

            val request = DeviceRegistrationRequestDto(
                token = token,
                deviceId = resolvedDeviceId,
                platform = PLATFORM,
                topics = topics,
                doNotDisturb = dnd?.let {
                    DoNotDisturbDto(
                        start = TIME_FORMATTER.format(it.start),
                        end = TIME_FORMATTER.format(it.end)
                    )
                }
            )

            val result = runCatching { api.registerDevice(request) }
            result.onSuccess {
                dataStore.edit { prefs ->
                    prefs[KEY_LAST_SYNCED_TOKEN] = token
                }
                Timber.d("FCM device registered successfully")
            }.onFailure { error ->
                val message = networkErrorMapper.map(error)
                Timber.w(error, "Failed to register FCM device: %s", message)
            }
        }
    }

    override suspend fun clearRegistration() {
        withContext(ioDispatcher) {
            val current = settings.first()
            val token = current.deviceToken ?: return@withContext
            unregisterRemote(token)
            dataStore.edit { prefs ->
                prefs.remove(KEY_DEVICE_TOKEN)
                prefs.remove(KEY_LAST_SYNCED_TOKEN)
            }
        }
    }

    private suspend fun unregisterRemote(token: String) {
        val encoded = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        runCatching { api.unregisterDevice(encoded) }
            .onSuccess { Timber.d("FCM device unregistered") }
            .onFailure { error ->
                val message = networkErrorMapper.map(error)
                Timber.w(error, "Failed to unregister FCM device: %s", message)
            }
    }

    private fun Preferences.toDomain(): NotificationSettings {
        val enabled = this[KEY_NOTIFICATIONS_ENABLED] ?: true
        val token = this[KEY_DEVICE_TOKEN]
        val lastSyncedToken = this[KEY_LAST_SYNCED_TOKEN]
        val topics = this[KEY_TOPICS]?.let { deserializeTopics(it) } ?: emptyList()
        val dndEnabled = this[KEY_DND_ENABLED] ?: false
        val dndStart = this[KEY_DND_START]?.let { runCatching { LocalTime.parse(it, TIME_FORMATTER) }.getOrNull() }
        val dndEnd = this[KEY_DND_END]?.let { runCatching { LocalTime.parse(it, TIME_FORMATTER) }.getOrNull() }
        return NotificationSettings(
            notificationsEnabled = enabled,
            topics = topics,
            deviceToken = token,
            lastSyncedToken = lastSyncedToken,
            doNotDisturb = DoNotDisturb(
                enabled = dndEnabled,
                start = dndStart,
                end = dndEnd
            )
        )
    }

    private fun serializeTopics(topics: List<String>): String =
        json.encodeToString(TOPICS_SERIALIZER, topics)

    private fun deserializeTopics(payload: String): List<String> =
        runCatching { json.decodeFromString(TOPICS_SERIALIZER, payload) }
            .getOrDefault(emptyList())

    private fun currentDeviceId(): String? = runCatching {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        androidId?.takeIf { it.isNotBlank() }
    }.getOrNull()

    companion object {
        private const val PLATFORM = "android"
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_DEVICE_TOKEN = stringPreferencesKey("notifications_device_token")
        private val KEY_LAST_SYNCED_TOKEN = stringPreferencesKey("notifications_last_synced_token")
        private val KEY_TOPICS = stringPreferencesKey("notifications_topics")
        private val KEY_DND_ENABLED = booleanPreferencesKey("notifications_dnd_enabled")
        private val KEY_DND_START = stringPreferencesKey("notifications_dnd_start")
        private val KEY_DND_END = stringPreferencesKey("notifications_dnd_end")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val TOPICS_SERIALIZER = ListSerializer(String.serializer())
    }
}
