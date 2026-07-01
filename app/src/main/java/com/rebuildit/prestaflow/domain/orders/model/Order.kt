package com.rebuildit.prestaflow.domain.orders.model

import kotlinx.serialization.Serializable

data class Order(
    val id: Long,
    val reference: String,
    val status: String,
    val totalPaid: Double,
    val currency: String,
    val customerName: String,
    /** Date de création de la commande (ISO-8601). Utilisée dans la liste. */
    val createdAtIso: String,
    /** Date de dernière modification de la commande (ISO-8601). */
    val updatedAtIso: String,
    val hasInvoice: Boolean = false,
    val hasShippingLabel: Boolean = false,
    val items: List<OrderItem> = emptyList(),
    val shipping: OrderShipping? = null,
    /**
     * Couleur hexadécimale du statut (`#RRGGBB`) retournée par le connecteur v1.9+.
     * Null si le connecteur n'est pas encore déployé (fallback depuis [availableStatuses]).
     */
    val statusColor: String? = null,
    /**
     * ID numérique du statut PrestaShop — retourné par l'endpoint liste v1.9+.
     * 0 si absent (ancien connecteur).
     */
    val currentStateId: Int = 0,
    /**
     * ID PrestaShop du client — renvoyé par l'endpoint détail (chantier connecteur en cours).
     * Null si le connecteur ne le retourne pas encore (rétrocompatible).
     */
    val customerId: Long? = null,
)

@Serializable
data class OrderItem(
    val productId: Long,
    val name: String,
    val reference: String? = null,
    val quantity: Int,
    val price: Double,
    val imageUrl: String? = null,
)

@Serializable
data class OrderShipping(
    val carrierId: Long = 0,
    val carrierName: String,
    val trackingNumber: String?,
)
