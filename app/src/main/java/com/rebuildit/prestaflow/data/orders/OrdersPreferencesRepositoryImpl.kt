package com.rebuildit.prestaflow.data.orders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

        companion object {
            private val KEY_VISIBLE_STATUS_IDS = stringPreferencesKey("orders_visible_status_ids")
        }
    }
