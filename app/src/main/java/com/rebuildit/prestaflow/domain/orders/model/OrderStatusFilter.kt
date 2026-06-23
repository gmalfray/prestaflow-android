package com.rebuildit.prestaflow.domain.orders.model

/** Statut de commande tel qu'exposé par l'endpoint `GET orders/statuses`. */
data class OrderStatusFilter(
    val id: Int,
    val name: String,
    val color: String,
)
