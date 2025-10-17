package com.rebuildit.prestaflow.data.orders.mapper

import com.rebuildit.prestaflow.data.local.entity.OrderEntity
import com.rebuildit.prestaflow.data.remote.dto.OrderDto
import com.rebuildit.prestaflow.domain.orders.model.Order

fun OrderEntity.toDomain(): Order = Order(
    id = id,
    reference = reference,
    status = status,
    totalPaid = totalPaid,
    currency = currency,
    customerName = customerName,
    updatedAtIso = updatedAtIso
)

fun OrderDto.toEntity(): OrderEntity = OrderEntity(
    id = id,
    reference = reference,
    status = status,
    totalPaid = totalPaid,
    currency = currency,
    customerName = "${customer.firstName} ${customer.lastName}",
    updatedAtIso = dateUpdated
)
