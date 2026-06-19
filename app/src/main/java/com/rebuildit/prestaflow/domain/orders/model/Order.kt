package com.rebuildit.prestaflow.domain.orders.model

import kotlinx.serialization.Serializable

data class Order(
    val id: Long,
    val reference: String,
    val status: String,
    val totalPaid: Double,
    val currency: String,
    val customerName: String,
    val updatedAtIso: String,
    val items: List<OrderItem> = emptyList(),
    val shipping: OrderShipping? = null
)

@Serializable
data class OrderItem(
    val productId: Long,
    val name: String,
    val reference: String? = null,
    val quantity: Int,
    val price: Double
)

@Serializable
data class OrderShipping(
    val carrierId: Long = 0,
    val carrierName: String,
    val trackingNumber: String?
)
