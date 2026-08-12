package com.rebuildit.prestaflow.data.orders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import com.rebuildit.prestaflow.domain.orders.model.OrdersSeenState
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

        // ─── Pastille "commandes non vues" (par boutique) ───────────────────────

        override fun ordersSeenState(shopId: String): Flow<OrdersSeenState> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture état commandes vues")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs ->
                    OrdersSeenState(
                        lastSeenOrderId = prefs[lastSeenOrderIdKey(shopId)] ?: 0L,
                        individuallySeenIds = prefs[individuallySeenIdsKey(shopId)].toIdSet(),
                    )
                }
                .distinctUntilChanged()

        override suspend fun markOrdersListSeen(
            shopId: String,
            maxOrderId: Long,
        ) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    val lastSeenKey = lastSeenOrderIdKey(shopId)
                    val newLastSeen = maxOf(prefs[lastSeenKey] ?: 0L, maxOrderId)
                    prefs[lastSeenKey] = newLastSeen

                    // Purge les IDs vus individuellement désormais couverts par le nouveau repère,
                    // pour que l'ensemble ne grossisse pas indéfiniment (cf. Javadoc interface).
                    val individualKey = individuallySeenIdsKey(shopId)
                    val remaining = prefs[individualKey].toIdSet().filter { it > newLastSeen }.toSet()
                    if (remaining.isEmpty()) {
                        prefs.remove(individualKey)
                    } else {
                        prefs[individualKey] = remaining.joinToString(",")
                    }
                }
            }
        }

        override suspend fun markOrderSeen(
            shopId: String,
            orderId: Long,
        ) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    val alreadyCoveredByLastSeen = orderId <= (prefs[lastSeenOrderIdKey(shopId)] ?: 0L)
                    if (alreadyCoveredByLastSeen) return@edit
                    val individualKey = individuallySeenIdsKey(shopId)
                    val updated = prefs[individualKey].toIdSet() + orderId
                    prefs[individualKey] = updated.joinToString(",")
                }
            }
        }

        companion object {
            private val KEY_VISIBLE_STATUS_IDS = stringPreferencesKey("orders_visible_status_ids")

            /** Repère "dernière commande vue" — une clé DataStore distincte PAR BOUTIQUE ([shopId]). */
            private fun lastSeenOrderIdKey(shopId: String) = longPreferencesKey("orders_last_seen_id_$shopId")

            /** IDs vus individuellement au-delà du repère — une clé DataStore distincte PAR BOUTIQUE. */
            private fun individuallySeenIdsKey(shopId: String) = stringPreferencesKey("orders_seen_individual_ids_$shopId")

            /** Parse une liste d'IDs sérialisée en CSV (même format que [KEY_VISIBLE_STATUS_IDS]). */
            private fun String?.toIdSet(): Set<Long> {
                return this?.split(",")?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty()
            }
        }
    }
