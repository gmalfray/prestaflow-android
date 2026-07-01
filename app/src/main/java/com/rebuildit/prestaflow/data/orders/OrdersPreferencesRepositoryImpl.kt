package com.rebuildit.prestaflow.data.orders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrdersPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val ioDispatcher: CoroutineDispatcher,
    ) : OrdersPreferencesRepository {
        override val visibleStatusIds: Flow<Set<Int>?> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférences statuts commandes")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs ->
                    prefs[KEY_VISIBLE_STATUS_IDS]?.let { raw ->
                        raw.split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .toSet()
                    }
                    // null = pas de préférence enregistrée → tous les statuts affichés
                }
                .distinctUntilChanged()

        override suspend fun setVisibleStatusIds(ids: Set<Int>) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    prefs[KEY_VISIBLE_STATUS_IDS] = ids.joinToString(",")
                }
            }
        }

        override suspend fun clearVisibleStatusIds() {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    prefs.remove(KEY_VISIBLE_STATUS_IDS)
                }
            }
        }

        // ─── Swipe configurable ───────────────────────────────────────────────

        override val swipeEnabled: Flow<Boolean> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférence swipe activé")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs -> prefs[KEY_SWIPE_ENABLED] ?: true }
                .distinctUntilChanged()

        override val swipeSourceStatusId: Flow<Int?> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférence swipe source")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs -> prefs[KEY_SWIPE_SOURCE_STATUS_ID] }
                .distinctUntilChanged()

        override val swipeLeftTargetStatusId: Flow<Int?> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférence swipe gauche")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs -> prefs[KEY_SWIPE_LEFT_TARGET_STATUS_ID] }
                .distinctUntilChanged()

        override val swipeRightTargetStatusId: Flow<Int?> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférence swipe droite")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs -> prefs[KEY_SWIPE_RIGHT_TARGET_STATUS_ID] }
                .distinctUntilChanged()

        override suspend fun setSwipeEnabled(enabled: Boolean) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    prefs[KEY_SWIPE_ENABLED] = enabled
                }
            }
        }

        override suspend fun setSwipeSourceStatusId(id: Int?) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    if (id != null) prefs[KEY_SWIPE_SOURCE_STATUS_ID] = id
                    else prefs.remove(KEY_SWIPE_SOURCE_STATUS_ID)
                }
            }
        }

        override suspend fun setSwipeLeftTargetStatusId(id: Int?) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    if (id != null) prefs[KEY_SWIPE_LEFT_TARGET_STATUS_ID] = id
                    else prefs.remove(KEY_SWIPE_LEFT_TARGET_STATUS_ID)
                }
            }
        }

        override suspend fun setSwipeRightTargetStatusId(id: Int?) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    if (id != null) prefs[KEY_SWIPE_RIGHT_TARGET_STATUS_ID] = id
                    else prefs.remove(KEY_SWIPE_RIGHT_TARGET_STATUS_ID)
                }
            }
        }

        companion object {
            private val KEY_VISIBLE_STATUS_IDS = stringPreferencesKey("orders_visible_status_ids")
            private val KEY_SWIPE_ENABLED = booleanPreferencesKey("orders_swipe_enabled")
            private val KEY_SWIPE_SOURCE_STATUS_ID = intPreferencesKey("orders_swipe_source_status_id")
            private val KEY_SWIPE_LEFT_TARGET_STATUS_ID = intPreferencesKey("orders_swipe_left_target_status_id")
            private val KEY_SWIPE_RIGHT_TARGET_STATUS_ID = intPreferencesKey("orders_swipe_right_target_status_id")
        }
    }
