package com.rebuildit.prestaflow.data.products

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebuildit.prestaflow.domain.products.StockReplenishPreferencesRepository
import com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS
import com.rebuildit.prestaflow.domain.products.model.normalizeQuickAddAmounts
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

/**
 * Implémentation DataStore de [StockReplenishPreferencesRepository] — même pattern que
 * [com.rebuildit.prestaflow.data.orders.OrdersPreferencesRepositoryImpl] (liste d'IDs sérialisée en
 * chaîne CSV dans le DataStore `Preferences` unique de l'app).
 */
@Singleton
class StockReplenishPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val ioDispatcher: CoroutineDispatcher,
    ) : StockReplenishPreferencesRepository {
        override val quickAddAmounts: Flow<List<Int>> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférences boutons rapides réappro")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs ->
                    val raw = prefs[KEY_QUICK_ADD_AMOUNTS]
                    if (raw == null) {
                        DEFAULT_QUICK_ADD_AMOUNTS
                    } else {
                        val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
                        normalizeQuickAddAmounts(parsed)
                    }
                }
                .distinctUntilChanged()

        override suspend fun setQuickAddAmounts(amounts: List<Int>) {
            val normalized = normalizeQuickAddAmounts(amounts)
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    prefs[KEY_QUICK_ADD_AMOUNTS] = normalized.joinToString(",")
                }
            }
        }

        companion object {
            private val KEY_QUICK_ADD_AMOUNTS = stringPreferencesKey("stock_replenish_quick_add_amounts")
        }
    }
