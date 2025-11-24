package com.rebuildit.prestaflow.data.orders.mapper

import com.rebuildit.prestaflow.data.local.entity.OrderEntity
import com.rebuildit.prestaflow.data.remote.dto.OrderDto
import com.rebuildit.prestaflow.domain.orders.model.Order

import com.rebuildit.prestaflow.data.remote.dto.OrderItemDto
import com.rebuildit.prestaflow.data.remote.dto.OrderShippingDto
import com.rebuildit.prestaflow.domain.orders.model.OrderItem
import com.rebuildit.prestaflow.domain.orders.model.OrderShipping
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun OrderEntity.toDomain(): Order {
    val itemsList = itemsJson?.let {
        try {
            Json.decodeFromString<List<OrderItem>>(it)
        } catch (e: Exception) {
            emptyList()
        }
    } ?: emptyList()

    val shippingInfo = shippingJson?.let {
        try {
            Json.decodeFromString<OrderShipping>(it)
        } catch (e: Exception) {
            null
        }
    }

    return Order(
        id = id,
        reference = reference,
        status = status,
        totalPaid = totalPaid,
        currency = currency,
        customerName = customerName,
        updatedAtIso = updatedAtIso,
        items = itemsList,
        shipping = shippingInfo
    )
}

fun OrderDto.toEntity(): OrderEntity {
    val itemsJsonStr = items?.let { dtos ->
        val domainItems = dtos.map { it.toDomain() }
        Json.encodeToString(domainItems)
    }
    
    val shippingJsonStr = shipping?.let { dto ->
        val domainShipping = dto.toDomain()
        Json.encodeToString(domainShipping)
    }

    return OrderEntity(
        id = id,
        reference = reference,
        status = status,
        totalPaid = totalPaid,
        currency = currency,
        customerName = "${customer.firstName} ${customer.lastName}",
        updatedAtIso = dateUpdated,
        itemsJson = itemsJsonStr,
        shippingJson = shippingJsonStr
    )
}

fun OrderItemDto.toDomain() = OrderItem(
    productId = productId,
    name = name,
    quantity = quantity,
    price = priceTaxIncl
)

fun OrderShippingDto.toDomain() = OrderShipping(
    carrierName = carrierName,
    trackingNumber = trackingNumber
)
