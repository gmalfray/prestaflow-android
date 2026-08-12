package com.rebuildit.prestaflow.domain.orders.model

/**
 * État "commandes vues" d'une boutique, utilisé pour piloter la pastille de l'onglet Commandes.
 *
 * Deux façons de marquer une commande comme vue, qui doivent coexister (cf. demande Greg) :
 * - **Consulter la liste** avance [lastSeenOrderId] jusqu'au plus haut ID chargé
 *   ([OrdersPreferencesRepository.markOrdersListSeen]) — pas besoin d'ouvrir chaque commande.
 * - **Ouvrir une commande depuis une notification** ajoute son ID à [individuallySeenIds]
 *   ([OrdersPreferencesRepository.markOrderSeen]) SANS avancer le repère : une commande plus
 *   récente ouverte isolément ne doit pas faire disparaître les commandes plus anciennes non
 *   encore consultées (ex. 6579 ouverte depuis une notif, 6577/6578 encore non vues → seule 6579
 *   doit sortir du compteur).
 *
 * [individuallySeenIds] n'a besoin de retenir que les IDs strictement supérieurs à
 * [lastSeenOrderId] : tout ID inférieur ou égal est déjà couvert par le repère, donc purgé côté
 * stockage (cf. [OrdersPreferencesRepository.markOrdersListSeen]) pour ne pas grossir indéfiniment.
 */
data class OrdersSeenState(
    val lastSeenOrderId: Long = 0L,
    val individuallySeenIds: Set<Long> = emptySet(),
) {
    /** Vrai si [orderId] doit être compté comme "non vu" dans la pastille. */
    fun isUnseen(orderId: Long): Boolean = orderId > lastSeenOrderId && orderId !in individuallySeenIds
}
