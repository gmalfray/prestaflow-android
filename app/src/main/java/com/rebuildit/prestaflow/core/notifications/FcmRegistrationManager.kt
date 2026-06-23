package com.rebuildit.prestaflow.core.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.notifications.NotificationCategoriesRepository
import com.rebuildit.prestaflow.domain.notifications.NotificationSettings
import com.rebuildit.prestaflow.domain.notifications.NotificationsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmRegistrationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val authRepository: AuthRepository,
        private val notificationsRepository: NotificationsRepository,
        private val notificationCategoriesRepository: NotificationCategoriesRepository,
        private val shopDeviceRegistrar: ShopDeviceRegistrar,
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

        @Volatile
        private var initialized = false

        // Dernière boutique active connue : si elle change, on réenregistre le device.
        private var lastActiveShopId: String? = null

        // Boutiques connues à la dernière émission : pour détecter les suppressions.
        private var knownConnections: List<ShopConnection> = emptyList()

        fun initialize() {
            if (initialized) return
            if (!ensureFirebaseApp()) {
                Timber.w("FirebaseApp initialization failed, skipping FCM registration")
                return
            }
            initialized = true
            // Observe les changements de catégories → ré-enregistre sur toutes les boutiques.
            scope.launch {
                notificationCategoriesRepository.categoryPreferences
                    .drop(1) // ignore la valeur initiale déjà traitée par le flux principal
                    .collectLatest { _ ->
                        val currentSettings = notificationsRepository.settings.first()
                        val fcmToken = currentSettings.deviceToken ?: return@collectLatest
                        val topics = notificationCategoriesRepository.enabledTopics()
                        val connections = authRepository.connections.value
                        if (topics.isEmpty()) {
                            // Aucune catégorie active → désenregistrer de toutes les boutiques
                            Timber.i("Toutes catégories désactivées → désenregistrement FCM sur %d boutiques", connections.size)
                            connections.forEach { connection ->
                                shopDeviceRegistrar.unregisterFromShop(
                                    connection.shopUrl,
                                    connection.token.value,
                                    fcmToken,
                                )
                            }
                            notificationsRepository.markRegistrationStale()
                        } else {
                            // Ré-enregistrer sur toutes les boutiques avec les nouveaux topics
                            Timber.i("Catégories modifiées → ré-enregistrement FCM (topics=%s)", topics)
                            connections.forEach { connection ->
                                shopDeviceRegistrar.registerOnShop(
                                    connection.shopUrl,
                                    connection.token.value,
                                    fcmToken,
                                    topics,
                                )
                            }
                            // La boutique active via le flux normal
                            notificationsRepository.updateTopics(topics)
                        }
                    }
            }
            scope.launch {
                combine(
                    authRepository.authState,
                    notificationsRepository.settings,
                    authRepository.connections,
                ) { authState, settings, connections ->
                    Triple(authState, settings, connections)
                }.collectLatest { (authState, settings, connections) ->
                    val activeShopId = connections.firstOrNull { it.isActive }?.id

                    // Boutiques supprimées → désenregistrer le device de leur backend.
                    val fcmToken = settings.deviceToken
                    if (fcmToken != null) {
                        knownConnections
                            .filter { known -> connections.none { it.id == known.id } }
                            .forEach { removed ->
                                Timber.i("Boutique supprimée → désenregistrement FCM (%s)", removed.shopUrl)
                                shopDeviceRegistrar.unregisterFromShop(removed.shopUrl, removed.token.value, fcmToken)
                            }
                    }
                    knownConnections = connections

                    // Bascule de boutique → réenregistrer le device sur la nouvelle active.
                    if (authState is AuthState.Authenticated &&
                        activeShopId != null &&
                        lastActiveShopId != null &&
                        activeShopId != lastActiveShopId
                    ) {
                        Timber.i("Boutique active changée → réenregistrement FCM")
                        notificationsRepository.markRegistrationStale()
                    }
                    lastActiveShopId = activeShopId
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
                val topics = notificationCategoriesRepository.enabledTopics()
                // La boutique active passe par le flux normal (met à jour le token local + isTokenSynced).
                notificationsRepository.syncRegistration(token, null)
                // Les autres boutiques connectées : on (ré)enregistre le nouveau token sur chacune,
                // sinon elles garderaient l'ancien token périmé et cesseraient de pousser.
                authRepository.connections.value.forEach { connection ->
                    shopDeviceRegistrar.registerOnShop(connection.shopUrl, connection.token.value, token, topics)
                }
            }
        }

        private suspend fun handleState(
            authState: AuthState,
            settings: NotificationSettings,
        ) {
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

        @Suppress("ReturnCount") // Séquence guard-clause : null-check token existant, null-check fetch
        private suspend fun ensureDeviceToken(settings: NotificationSettings): String? {
            val existing = settings.deviceToken
            if (existing != null) return existing
            val token = fetchToken() ?: return null
            notificationsRepository.syncRegistration(token, null)
            return token
        }

        private suspend fun fetchToken(): String? =
            runCatching {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            }.onFailure { error ->
                Timber.w(error, "Unable to fetch FCM token")
            }.getOrNull()

        // FirebaseApp.initializeApp peut lancer des RuntimeException non documentées
        @Suppress("TooGenericExceptionCaught")
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
