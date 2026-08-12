package com.rebuildit.prestaflow.domain.orders

import com.rebuildit.prestaflow.domain.orders.model.OrdersSeenState
import kotlinx.coroutines.flow.Flow

/** Préférences utilisateur liées à l'écran Commandes. */
interface OrdersPreferencesRepository {
    /**
     * Flux des IDs de statuts sélectionnés pour la barre de filtres.
     * Null = aucune préférence enregistrée → afficher tous les statuts (comportement par défaut).
     */
    val visibleStatusIds: Flow<Set<Int>?>

    /** Persiste l'ensemble des statuts à afficher dans la barre de filtres. */
    suspend fun setVisibleStatusIds(ids: Set<Int>)

    /** Réinitialise la préférence (retour au comportement par défaut : tous les statuts). */
    suspend fun clearVisibleStatusIds()

    // ─── Pastille "commandes non vues" (onglet Commandes) ──────────────────────

    /**
     * État "commandes vues" de la boutique [shopId], mémorisé PAR BOUTIQUE : l'app est
     * multi-boutiques, changer de boutique active ne doit jamais faire disparaître ni mélanger
     * les commandes non vues d'une autre boutique. Cf. [OrdersSeenState].
     */
    fun ordersSeenState(shopId: String): Flow<OrdersSeenState>

    /**
     * Avance le repère "dernière commande vue" de la boutique [shopId] jusqu'à [maxOrderId] —
     * appelé après un chargement RÉUSSI de la liste (jamais après une erreur ou un écran vide,
     * sinon la pastille disparaîtrait sans que rien n'ait été vu). Purge au passage les IDs
     * [OrdersSeenState.individuallySeenIds] désormais couverts par ce nouveau repère, pour que cet
     * ensemble ne grossisse pas indéfiniment.
     */
    suspend fun markOrdersListSeen(
        shopId: String,
        maxOrderId: Long,
    )

    /**
     * Marque la seule commande [orderId] comme vue pour la boutique [shopId] (ouverture depuis une
     * notification, sans passer par la liste) — n'affecte PAS le repère : les autres commandes non
     * vues restent au compteur. No-op si [orderId] est déjà couvert par le repère.
     */
    suspend fun markOrderSeen(
        shopId: String,
        orderId: Long,
    )
}
