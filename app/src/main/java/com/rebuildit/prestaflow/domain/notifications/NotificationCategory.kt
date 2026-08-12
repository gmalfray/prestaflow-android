package com.rebuildit.prestaflow.domain.notifications

/**
 * Catégories de notifications supportées par le connecteur.
 * [key] correspond à la valeur exacte envoyée dans le champ `topics` lors de l'enregistrement
 * du device (`POST /notifications/devices`).
 * [defaultEnabled] est l'état appliqué tant que l'utilisateur n'a jamais touché à l'interrupteur
 * de cette catégorie (cf. [com.rebuildit.prestaflow.data.notifications.NotificationCategoriesRepositoryImpl]).
 * La plupart des catégories sont activées par défaut ; [REVIEW_PENDING] fait exception pour rester
 * cohérente avec le réglage `review_pending_alerts_enabled` désactivé par défaut côté boutique.
 */
enum class NotificationCategory(val key: String, val defaultEnabled: Boolean = true) {
    ORDER_CREATED("order.created"),
    ORDER_STATUS_CHANGED("order.status.changed"),
    ORDER_SHIPPING_UPDATED("order.shipping.updated"),
    STOCK_LOW("stock.low"),

    /**
     * Panne du tunnel de paiement de la boutique. Émise par la surveillance côté serveur
     * (script `watch-payments.sh`), pas par le module : le module ne voit pas les échecs
     * du prestataire de paiement, seul son journal les connaît.
     */
    PAYMENT_ERROR("shop.payment.error"),

    /**
     * Nouveau message d'une cliente sur un fil SAV natif. Émise par le connecteur (hook
     * `actionObjectCustomerMessageAddAfter`), seulement quand `sav_message_alerts_enabled` est
     * actif côté boutique (vrai en prod). Activée par défaut côté app : c'est la notification
     * utile au quotidien, une cliente attend une réponse.
     */
    SAV_MESSAGE("sav.message"),

    /**
     * Nouvel avis entré en file de modération. Émise par le connecteur (hook
     * `actionObjectRbReviewAddAfter`), seulement quand `review_pending_alerts_enabled` est actif
     * côté boutique (FAUX en prod : la modération native n'est pas utilisée sur pensebonheur.fr
     * aujourd'hui). Désactivée par défaut côté app pour rester cohérente avec ce réglage — pas
     * une urgence, de l'information.
     */
    REVIEW_PENDING("review.pending", defaultEnabled = false),
    ;

    companion object {
        /** Toutes les clés connues du connecteur, quel que soit leur état activé/désactivé. */
        val allKeys: List<String> = entries.map { it.key }
    }
}
