package com.rebuildit.prestaflow.domain.products.model

/**
 * Préférences des boutons rapides de l'écran de réappro stock (Lot 2, cf.
 * [com.rebuildit.prestaflow.domain.products.StockReplenishPreferencesRepository]).
 *
 * Défaut = comportement du Lot 1 (boutons fixes +5/+10/+20) : tant que l'utilisateur n'a jamais
 * configuré ses propres montants, rien ne change visuellement.
 */
val DEFAULT_QUICK_ADD_AMOUNTS = listOf(5, 10, 20)

/** Nombre minimum de boutons rapides configurables (au moins un). */
const val MIN_QUICK_ADD_BUTTONS = 1

/** Nombre maximum de boutons rapides configurables. */
const val MAX_QUICK_ADD_BUTTONS = 5

/**
 * Normalise une liste de montants de boutons rapides avant persistance/affichage :
 * - ne garde que les entiers strictement positifs (une saisie invalide est silencieusement écartée
 *   plutôt que de faire planter la désérialisation ou d'afficher un bouton "+0"/"−N") ;
 * - borne le nombre de boutons à [MAX_QUICK_ADD_BUTTONS] ;
 * - retombe sur [DEFAULT_QUICK_ADD_AMOUNTS] si la liste résultante est vide (aucune préférence
 *   valide enregistrée → comportement Lot 1 inchangé).
 */
fun normalizeQuickAddAmounts(amounts: List<Int>): List<Int> {
    val valid = amounts.filter { it > 0 }.take(MAX_QUICK_ADD_BUTTONS)
    return valid.ifEmpty { DEFAULT_QUICK_ADD_AMOUNTS }
}
