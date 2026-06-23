package com.rebuildit.prestaflow.domain.orders

import com.rebuildit.prestaflow.domain.orders.model.Order
import kotlinx.coroutines.flow.Flow

interface OrdersRepository {
    fun observeOrders(): Flow<List<Order>>

    suspend fun refresh(forceRemote: Boolean = false)

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
}
