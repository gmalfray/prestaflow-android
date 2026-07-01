package com.rebuildit.prestaflow.domain.orders

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

    // ─── Préférences swipe configurable ──────────────────────────────────────

    /**
     * Flux d'activation du swipe.
     * Défaut : true (swipe activé si non configuré).
     */
    val swipeEnabled: Flow<Boolean>

    /**
     * Flux de l'ID du statut source (swipe n'est actif que sur ce statut).
     * Null = résolution par nom (matcher "paiement accepte") — comportement historique.
     */
    val swipeSourceStatusId: Flow<Int?>

    /**
     * Flux de l'ID du statut cible pour le swipe gauche (EndToStart).
     * Null = résolution par nom (matcher "preparation") — comportement historique.
     */
    val swipeLeftTargetStatusId: Flow<Int?>

    /**
     * Flux de l'ID du statut cible pour le swipe droite (StartToEnd).
     * Null = résolution par nom ("termin" puis "livr") — comportement historique.
     */
    val swipeRightTargetStatusId: Flow<Int?>

    suspend fun setSwipeEnabled(enabled: Boolean)

    suspend fun setSwipeSourceStatusId(id: Int?)

    suspend fun setSwipeLeftTargetStatusId(id: Int?)

    suspend fun setSwipeRightTargetStatusId(id: Int?)
}
