package com.rebuildit.prestaflow.data.clients.mapper

import com.rebuildit.prestaflow.data.local.entity.ClientEntity
import com.rebuildit.prestaflow.data.remote.dto.CustomerDto
import com.rebuildit.prestaflow.domain.clients.model.Client

fun ClientEntity.toDomain(): Client = Client(
    id = id,
    firstName = firstName,
    lastName = lastName,
    email = email,
    ordersCount = ordersCount,
    totalSpent = totalSpent,
    lastOrderAtIso = lastOrderIso
)

fun CustomerDto.toEntity(lastSyncedIso: String): ClientEntity = ClientEntity(
    id = id,
    firstName = firstName,
    lastName = lastName,
    email = email,
    ordersCount = ordersCount,
    totalSpent = totalSpent,
    lastOrderIso = lastOrderAt,
    lastSyncedIso = lastSyncedIso
)
