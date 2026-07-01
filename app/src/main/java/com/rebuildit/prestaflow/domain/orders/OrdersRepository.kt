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
     *
     * @param statusIds Si non vide, filtre les commandes par ces statuts (param `statuses` CSV).
     *   Ensemble vide = toutes les commandes.
     * @param sort Ordre de tri : `date_desc` (défaut), `date_asc`, `total_desc`, `total_asc`,
     *   `status`, `reference`.
     * @param dateFrom Date de début au format Y-m-d. Null = pas de filtre date.
     * @param dateTo Date de fin au format Y-m-d. Null = pas de filtre date.
     * @param offset Décalage pour la pagination (0 = première page, vide Room avant upsert).
     * @param limit Nombre de commandes demandées (défaut [DEFAULT_PAGE_SIZE]).
     * @return `true` si d'autres commandes sont probablement disponibles (la réponse était pleine).
     */
    suspend fun refresh(
        forceRemote: Boolean = false,
        statusIds: Set<Int> = emptySet(),
        sort: String = "date_desc",
        dateFrom: String? = null,
        dateTo: String? = null,
        offset: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Boolean

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }

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
