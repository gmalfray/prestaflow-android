package com.rebuildit.prestaflow.domain.clients.model

import kotlinx.serialization.Serializable

@Serializable
data class Client(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val ordersCount: Int,
    val totalSpent: Double,
    val lastOrderAtIso: String?,
    val orders: List<ClientOrder> = emptyList(),
) {
    val fullName: String
        get() =
            listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
}

@Serializable
data class ClientOrder(
    val id: Long,
    val reference: String,
    val status: String,
    val totalPaid: Double,
    val currency: String,
    val dateAdded: String? = null,
)
