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
}
