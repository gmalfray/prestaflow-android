package com.rebuildit.prestaflow.data.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.rebuildit.prestaflow.domain.notifications.NotificationCategoriesRepository
import com.rebuildit.prestaflow.domain.notifications.NotificationCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationCategoriesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val ioDispatcher: CoroutineDispatcher,
    ) : NotificationCategoriesRepository {
        override val categoryPreferences: Flow<Map<NotificationCategory, Boolean>> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférences catégories notifs")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs -> prefs.toDomain() }
                .distinctUntilChanged()

        override suspend fun setCategory(
            category: NotificationCategory,
            enabled: Boolean,
        ) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    prefs[category.prefKey()] = enabled
                }
            }
        }

        override suspend fun enabledTopics(): List<String> =
            withContext(ioDispatcher) {
                val prefs = dataStore.data.first()
                NotificationCategory.entries.filter { category ->
                    // Défaut = activé si la clé est absente
                    prefs[category.prefKey()] != false
                }.map { it.key }
            }

        private fun Preferences.toDomain(): Map<NotificationCategory, Boolean> =
            NotificationCategory.entries.associateWith { category ->
                // Défaut = true (toutes activées)
                this[category.prefKey()] != false
            }

        private fun NotificationCategory.prefKey(): Preferences.Key<Boolean> = booleanPreferencesKey("notif_category_$key")
    }
