package com.rebuildit.prestaflow.core.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.notifications.NotificationSettings
import com.rebuildit.prestaflow.domain.notifications.NotificationsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class FcmRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val notificationsRepository: NotificationsRepository,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        if (!ensureFirebaseApp()) {
            Timber.w("FirebaseApp initialization failed, skipping FCM registration")
            return
        }
        initialized = true
        scope.launch {
            combine(authRepository.authState, notificationsRepository.settings) { authState, settings ->
                authState to settings
            }.collectLatest { (authState, settings) ->
                handleState(authState, settings)
            }
        }
    }

    fun onNewToken(token: String) {
        if (!initialized && !ensureFirebaseApp()) {
            Timber.w("Firebase not ready, cannot process new token")
            return
        }
        scope.launch {
            notificationsRepository.syncRegistration(token, null)
        }
    }

    private suspend fun handleState(authState: AuthState, settings: NotificationSettings) {
        if (!settings.notificationsEnabled) {
            if (settings.deviceToken != null) {
                notificationsRepository.clearRegistration()
            }
            return
        }

        val token = ensureDeviceToken(settings) ?: return
        if (authState is AuthState.Authenticated && !settings.isTokenSynced) {
            notificationsRepository.syncRegistration(token, null)
        }
    }

    private suspend fun ensureDeviceToken(settings: NotificationSettings): String? {
        val existing = settings.deviceToken
        if (existing != null) return existing
        val token = fetchToken() ?: return null
        notificationsRepository.syncRegistration(token, null)
        return token
    }

    private suspend fun fetchToken(): String? = runCatching {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
    }.onFailure { error ->
        Timber.w(error, "Unable to fetch FCM token")
    }.getOrNull()

    private fun ensureFirebaseApp(): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val app = FirebaseApp.initializeApp(context)
                if (app == null) {
                    Timber.w("Firebase configuration missing; push notifications disabled")
                    return false
                }
            }
            true
        } catch (error: IllegalStateException) {
            Timber.w(error, "Firebase already initialized with different context")
            true
        } catch (error: Exception) {
            Timber.w(error, "Failed to initialize FirebaseApp")
            false
        }
    }
}
