package com.rebuildit.prestaflow.data.orders.mapper

import com.rebuildit.prestaflow.data.local.entity.OrderEntity
import com.rebuildit.prestaflow.data.remote.dto.OrderDto
import com.rebuildit.prestaflow.data.remote.dto.OrderItemDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListItemDto
import com.rebuildit.prestaflow.data.remote.dto.OrderShippingDto
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderItem
import com.rebuildit.prestaflow.domain.orders.model.OrderShipping
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

fun OrderEntity.toDomain(): Order {
    val itemsList =
        itemsJson?.let {
            try {
                Json.decodeFromString<List<OrderItem>>(it)
            } catch (e: SerializationException) {
                Timber.w(e, "Failed to deserialize order items from cached JSON")
                emptyList()
            }
        }.orEmpty()

    val shippingInfo =
        shippingJson?.let {
            try {
                Json.decodeFromString<OrderShipping>(it)
            } catch (e: SerializationException) {
                Timber.w(e, "Failed to deserialize order shipping from cached JSON")
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
        createdAtIso = createdAtIso,
        updatedAtIso = updatedAtIso,
        hasInvoice = hasInvoice,
        hasShippingLabel = hasShippingLabel,
        items = itemsList,
        shipping = shippingInfo,
        statusColor = statusColor,
        currentStateId = currentStateId,
        customerId = customerId,
    )
}

/**
 * Maps the FLAT order row returned by the list endpoint (`formatOrderRow`).
 * The list endpoint does not carry items/shipping so the JSON columns stay null.
 */
fun OrderListItemDto.toEntity(): OrderEntity =
    OrderEntity(
        id = id,
        reference = reference,
        status = status,
        totalPaid = totalPaid,
        currency = currency,
        customerName = "${customer.firstName} ${customer.lastName}".trim(),
        createdAtIso = dateAdded.orEmpty(),
        updatedAtIso = dateUpdated.orEmpty(),
        hasInvoice = hasInvoice,
        // non exposé par l'endpoint liste
        hasShippingLabel = false,
        itemsJson = null,
        shippingJson = null,
        statusColor = statusColor,
        currentStateId = currentStateId,
    )

/**
 * Maps the NESTED order object returned by the detail endpoint (`getOrderById`).
 */
fun OrderDto.toEntity(): OrderEntity {
    val itemsJsonStr =
        items?.let { dtos ->
            val domainItems = dtos.map { it.toDomain() }
            Json.encodeToString(domainItems)
        }

    val shippingJsonStr =
        shipping?.let { dto ->
            val domainShipping = dto.toDomain()
            Json.encodeToString(domainShipping)
        }

    return OrderEntity(
        id = id,
        reference = reference,
        status = status.name.orEmpty(),
        totalPaid = totals?.paidTaxIncl ?: 0.0,
        currency = totals?.currency.orEmpty(),
        customerName = "${customer.firstName} ${customer.lastName}".trim(),
        createdAtIso = dates?.createdAt.orEmpty(),
        updatedAtIso = dates?.updatedAt.orEmpty(),
        hasInvoice = hasInvoice,
        hasShippingLabel = shippingLabel?.hasShippingLabel ?: false,
        itemsJson = itemsJsonStr,
        shippingJson = shippingJsonStr,
        // L'endpoint détail ne retourne pas status_color / current_state_id : valeurs par défaut
        statusColor = null,
        currentStateId = status.id.toInt(),
        customerId = customerId,
    )
}

fun OrderItemDto.toDomain() =
    OrderItem(
        productId = productId,
        name = name,
        reference = reference,
        quantity = quantity,
        price = priceTaxIncl,
        imageUrl = imageUrl,
    )

fun OrderShippingDto.toDomain() =
    OrderShipping(
        carrierId = carrierId,
        carrierName = carrierName,
        trackingNumber = trackingNumber,
    )
