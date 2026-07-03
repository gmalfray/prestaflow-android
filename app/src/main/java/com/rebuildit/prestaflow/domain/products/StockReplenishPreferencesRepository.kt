package com.rebuildit.prestaflow.domain.products

import kotlinx.coroutines.flow.Flow

/**
 * Préférences utilisateur liées à l'écran « Ajout / réappro stock » (Lot 2 — boutons rapides
 * configurables, cf. [com.rebuildit.prestaflow.ui.products.StockReplenishViewModel]).
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
}
