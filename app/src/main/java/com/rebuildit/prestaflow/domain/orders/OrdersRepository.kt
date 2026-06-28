package com.rebuildit.prestaflow.domain.orders

import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import kotlinx.coroutines.flow.Flow

interface OrdersRepository {
    fun observeOrders(): Flow<List<Order>>

    /**
     * Retourne les statuts disponibles pour filtrer les commandes.
     * Lance une exception en cas d'erreur réseau.
     */
    suspend fun getOrderStatuses(): List<OrderStatusFilter>

    /**
     * Rafraîchit la liste des commandes depuis le serveur.
     * @param statusId Si non nul, filtre les commandes par ce statut.
     * @param dateFrom Date de début au format Y-m-d (ex. "2026-06-01"). Null = pas de filtre date.
     * @param dateTo Date de fin au format Y-m-d (ex. "2026-06-30"). Null = pas de filtre date.
     */
    suspend fun refresh(
        forceRemote: Boolean = false,
        statusId: Int? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
    )

    fun getOrder(orderId: Long): Flow<Order?>

    suspend fun refreshOrder(orderId: Long)

    /**
     * Updates the order status. [status] is the order state reference or label
     * (the connector resolves both). Refreshes the local cache on success.
     */
    suspend fun updateOrderStatus(
        orderId: Long,
        status: String,
    )

    /**
     * Updates the order shipping tracking number (and optional carrier).
     * Refreshes the local cache on success.
     */
    suspend fun updateOrderShipping(
        orderId: Long,
        trackingNumber: String,
        carrierId: Long? = null,
    )

    /**
     * Télécharge les octets PDF de la facture pour la commande [orderId].
     * Retourne `null` si la commande n'a pas de facture (HTTP 404).
     * Lance une exception pour toute autre erreur réseau.
     */
    suspend fun downloadInvoicePdf(orderId: Long): ByteArray?

    /**
     * Télécharge les octets PDF du bordereau de transport pour la commande [orderId].
     * Retourne `null` si la commande n'a pas de bordereau disponible (HTTP 404) :
     * transporteur non géré (ni Colissimo ni Mondial Relay), fichier absent ou URL expirée.
     * Lance une exception pour toute autre erreur réseau.
     */
    suspend fun downloadShippingLabel(orderId: Long): ByteArray?

    /**
     * Génère l'étiquette Colissimo pour la commande [orderId] via le webservice transporteur.
     * Rafraîchit le cache local en cas de succès (200/201).
     * Lance une [IllegalStateException] avec un message lisible en cas d'erreur métier :
     * - 422 carrier_not_supported → "Génération dispo uniquement pour Colissimo"
     * - 501 generation_not_configured → "Contrat transporteur non configuré"
     * - 502 carrier_webservice_error → "Erreur transporteur : <message>"
     */
    suspend fun generateShippingLabel(orderId: Long)
}
