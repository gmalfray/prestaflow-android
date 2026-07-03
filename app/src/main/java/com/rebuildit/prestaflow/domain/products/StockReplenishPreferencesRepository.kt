package com.rebuildit.prestaflow.domain.products

import kotlinx.coroutines.flow.Flow

/**
 * Préférences utilisateur liées à l'écran « Ajout / réappro stock » (Lot 2 — boutons rapides
 * configurables ; Lot 3 — bip sonore de confirmation, cf.
 * [com.rebuildit.prestaflow.ui.products.StockReplenishViewModel]).
 */
interface StockReplenishPreferencesRepository {
    /**
     * Flux des montants des boutons rapides de réappro, dans l'ordre d'affichage.
     * Défaut (aucune préférence enregistrée) : [com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS]
     * — comportement du Lot 1 (boutons fixes +5/+10/+20) inchangé.
     */
    val quickAddAmounts: Flow<List<Int>>

    /**
     * Persiste les montants des boutons rapides. La valeur est normalisée avant écriture (cf.
     * [com.rebuildit.prestaflow.domain.products.model.normalizeQuickAddAmounts]) : bornée à 1-5
     * boutons, entiers strictement positifs uniquement.
     */
    suspend fun setQuickAddAmounts(amounts: List<Int>)

    /**
     * Flux de l'activation du bip sonore de confirmation au scan (Lot 3). Défaut (aucune
     * préférence enregistrée) : **activé** — cf. [com.rebuildit.prestaflow.data.products.StockReplenishPreferencesRepositoryImpl]
     * pour la justification du choix. Le retour haptique, lui, n'est pas désactivable ici (respecte
     * déjà le réglage système via `LocalHapticFeedback`, cf. KDoc [com.rebuildit.prestaflow.ui.products.StockReplenishViewModel]).
     */
    val soundOnScan: Flow<Boolean>

    /** Persiste l'activation du bip sonore de confirmation au scan. */
    suspend fun setSoundOnScan(enabled: Boolean)
}
