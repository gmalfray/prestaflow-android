package com.rebuildit.prestaflow.domain.notifications

/**
 * Catégories de notifications supportées par le connecteur.
 * [key] correspond à la valeur exacte envoyée dans le champ `topics` lors de l'enregistrement
 * du device (`POST /notifications/devices`).
 */
enum class NotificationCategory(val key: String) {
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
    ;

    companion object {
        /** Renvoie toutes les clés → liste par défaut (toutes activées). */
        val allKeys: List<String> = entries.map { it.key }
    }
}
