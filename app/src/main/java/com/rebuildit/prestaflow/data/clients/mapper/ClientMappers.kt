package com.rebuildit.prestaflow.data.clients.mapper

import com.rebuildit.prestaflow.data.local.entity.ClientEntity
import com.rebuildit.prestaflow.data.remote.dto.CustomerDetailDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerOrderDto
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientOrder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun ClientEntity.toDomain(): Client {
    val orders =
        runCatching {
            json.decodeFromString<List<ClientOrder>>(ordersJson)
        }.getOrDefault(emptyList())

    return Client(
        id = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
        ordersCount = ordersCount,
        totalSpent = totalSpent,
        lastOrderAtIso = lastOrderIso,
        orders = orders,
        dateAddIso = dateAddIso,
    )
}

fun CustomerDto.toEntity(lastSyncedIso: String): ClientEntity =
    ClientEntity(
        id = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
        ordersCount = ordersCount,
        totalSpent = totalSpent,
        lastOrderIso = lastOrderAt,
        ordersJson = "[]",
        lastSyncedIso = lastSyncedIso,
        dateAddIso = dateAdd,
    )

fun CustomerDto.toDomain(): Client =
    Client(
        id = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
        ordersCount = ordersCount,
        totalSpent = totalSpent,
        lastOrderAtIso = lastOrderAt,
        dateAddIso = dateAdd,
    )

fun CustomerDetailDto.toEntity(lastSyncedIso: String): ClientEntity {
    val ordersJson = json.encodeToString(orders.map { it.toDomain() })

    return ClientEntity(
        id = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
        ordersCount = ordersCount,
        totalSpent = totalSpent,
        lastOrderIso = lastOrderAt,
        ordersJson = ordersJson,
        lastSyncedIso = lastSyncedIso,
    )
}

fun CustomerOrderDto.toDomain(): ClientOrder =
    ClientOrder(
        id = id,
        reference = reference,
        status = status,
        totalPaid = totalPaid,
        currency = currency,
        dateAdded = dateUpdated,
    )
