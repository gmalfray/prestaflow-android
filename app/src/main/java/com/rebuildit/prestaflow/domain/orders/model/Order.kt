package com.rebuildit.prestaflow.domain.orders.model

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

data class OrderItem(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val price: Double
)

data class OrderShipping(
    val carrierName: String,
    val trackingNumber: String?
)
