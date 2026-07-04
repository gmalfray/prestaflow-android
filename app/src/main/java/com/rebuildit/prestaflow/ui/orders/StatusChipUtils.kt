package com.rebuildit.prestaflow.ui.orders

import androidx.annotation.StringRes
import com.rebuildit.prestaflow.R

/*
 * Utilitaires pour les chips de filtre statut : libellés courts et accessibilité.
 *
 * Depuis que l'app reçoit les statuts de commande localisés côté serveur (header
 * Accept-Language), un mapping par MOT-CLÉ FRANÇAIS ne fonctionne plus dès que la boutique
 * répond dans une autre langue (ex. allemand) : aucun mot-clé ne matche, et même en cas de
 * match le libellé retourné restait en français.
 *
 * Le mapping se fait donc désormais par **ID de statut PrestaShop** (stable, indépendant de
 * la langue) → **ressource de libellé court localisée** ([STATUS_SHORT_LABEL_RES]). Pour un
 * statut dont l'ID n'est pas dans la table (statut custom d'une boutique), on retombe sur le
 * comportement historique : premier mot du nom (déjà localisé par le serveur) tronqué avec
 * ellipse ([statusShortLabelFallback]).
 */

/**
 * Table des correspondances ID de statut PrestaShop → ressource de libellé court.
 *
 * IDs standards PrestaShop, sauf mention contraire :
 * - 9 = « Terminée » est **spécifique à pensebonheur** (en PrestaShop vanilla, l'ID 9 correspond
 *   à « En attente de réappro payé »). On le mappe quand même ici car c'est le statut final
 *   utilisé par notre premier client ; une boutique où l'ID 9 a un autre sens verra un libellé
 *   « Terminé » incorrect sur ce chip précis — accepté pour l'instant, à revisiter si un 2ᵉ
 *   client utilise l'ID 9 différemment.
 */
private val STATUS_SHORT_LABEL_RES: Map<Int, Int> =
    mapOf(
        // 1 = Attente chèque
        1 to R.string.orders_status_short_1,
        // 2 = Paiement accepté
        2 to R.string.orders_status_short_2,
        // 3 = En préparation
        3 to R.string.orders_status_short_3,
        // 4 = Expédié
        4 to R.string.orders_status_short_4,
        // 5 = Livré
        5 to R.string.orders_status_short_5,
        // 6 = Annulé
        6 to R.string.orders_status_short_6,
        // 7 = Remboursé
        7 to R.string.orders_status_short_7,
        // 8 = Erreur de paiement
        8 to R.string.orders_status_short_8,
        // 9 = Terminée (pensebonheur)
        9 to R.string.orders_status_short_9,
        // 10 = Attente virement
        10 to R.string.orders_status_short_10,
    )

/** Longueur maximale du libellé court (premier mot, fallback). */
private const val SHORT_LABEL_MAX_LEN = 12

/**
 * Retourne la ressource de libellé court ([androidx.annotation.StringRes]) pour l'ID de statut
 * PrestaShop [id], ou `null` si cet ID n'est pas mappé (statut custom d'une boutique).
 *
 * Fonction pure (pas d'accès Compose), utilisable telle quelle depuis un test JVM.
 *
 * @param id ID de statut PrestaShop tel que renvoyé par l'API (`OrderStatusFilter.id`).
 */
@StringRes
internal fun statusShortLabelResId(id: Int): Int? = STATUS_SHORT_LABEL_RES[id]

/**
 * Libellé court de repli pour un statut dont l'ID n'est pas mappé dans [STATUS_SHORT_LABEL_RES]
 * (statut custom d'une boutique) : premier mot de [name] (déjà localisé par le serveur), tronqué
 * à [SHORT_LABEL_MAX_LEN] caractères avec une ellipse propre si nécessaire.
 *
 * @param name Nom complet du statut tel que renvoyé par l'API (ex. « En cours de préparation »).
 * @return Libellé court adapté au chip (ex. « En »).
 */
internal fun statusShortLabelFallback(name: String): String {
    if (name.isBlank()) return name
    val firstWord = name.trim().split(Regex("\\s+")).firstOrNull() ?: name.trim()
    return if (firstWord.length > SHORT_LABEL_MAX_LEN) {
        "${firstWord.take(SHORT_LABEL_MAX_LEN - 1)}…"
    } else {
        firstWord
    }
}
