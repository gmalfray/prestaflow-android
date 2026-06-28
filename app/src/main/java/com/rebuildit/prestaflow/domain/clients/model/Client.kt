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
    /** Date d'inscription (`date_add` PrestaShop, format `YYYY-MM-DD HH:MM:SS`). Null pour les clients venant de `customers/top`. */
    val dateAddIso: String? = null,
) {
    val fullName: String
        get() =
            listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
}

/**
 * Page paginée de clients issue de `GET /customers`.
 *
 * @param clients Liste des clients de cette page.
 * @param hasNext `true` si une page suivante est disponible côté serveur.
 * @param nextOffset Offset à passer pour charger la page suivante (`0` si [hasNext] est `false`).
 */
data class ClientsPage(
    val clients: List<Client>,
    val hasNext: Boolean,
    val nextOffset: Int,
)

@Serializable
data class ClientOrder(
    val id: Long,
    val reference: String,
    val status: String,
    val totalPaid: Double,
    val currency: String,
    val dateAdded: String? = null,
)
