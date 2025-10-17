package com.rebuildit.prestaflow.domain.orders.model

data class Order(
    val id: Long,
    val reference: String,
    val status: String,
    val totalPaid: Double,
    val currency: String,
    val customerName: String,
    val updatedAtIso: String
)
