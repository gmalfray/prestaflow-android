package com.rebuildit.prestaflow.domain.clients.model

data class Client(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val ordersCount: Int,
    val totalSpent: Double,
    val lastOrderAtIso: String?
) {
    val fullName: String
        get() = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
