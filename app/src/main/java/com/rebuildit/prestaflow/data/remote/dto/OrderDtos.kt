package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderListResponseDto(
    @SerialName("orders") val orders: List<OrderDto>,
    @SerialName("pagination") val pagination: PaginationDto? = null
)

@Serializable
data class OrderDto(
    @SerialName("id") val id: Long,
    @SerialName("reference") val reference: String,
    @SerialName("status") val status: String,
    @SerialName("payment") val payment: String? = null,
    @SerialName("currency") val currency: String,
    @SerialName("total_paid") val totalPaid: Double,
    @SerialName("date_add") val dateAdd: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("carrier") val carrier: CarrierDto? = null,
    @SerialName("customer") val customer: CustomerSummaryDto,
    @SerialName("items") val items: List<OrderItemDto>,
    @SerialName("history") val history: List<OrderHistoryDto> = emptyList()
)

@Serializable
data class OrderItemDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("name") val name: String,
    @SerialName("sku") val sku: String? = null,
    @SerialName("quantity") val quantity: Int,
    @SerialName("price_unit") val unitPrice: Double,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class OrderHistoryDto(
    @SerialName("status") val status: String,
    @SerialName("changed_by") val changedBy: String,
    @SerialName("changed_at") val changedAt: String,
    @SerialName("message") val message: String? = null
)

@Serializable
data class ShippingUpdateRequestDto(
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("carrier_id") val carrierId: Long? = null
)

@Serializable
data class CarrierDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String
)

@Serializable
data class CustomerSummaryDto(
    @SerialName("id") val id: Long,
    @SerialName("firstname") val firstName: String,
    @SerialName("lastname") val lastName: String,
    @SerialName("email") val maskedEmail: String? = null
)

@Serializable
data class PaginationDto(
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int
)
