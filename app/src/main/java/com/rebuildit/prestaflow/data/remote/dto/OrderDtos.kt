package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderListDto(
    @SerialName("orders") val orders: List<OrderDto>
)

@Serializable
data class OrderDto(
    @SerialName("id") val id: Long,
    @SerialName("reference") val reference: String,
    @SerialName("status") val status: String,
    @SerialName("total_paid") val totalPaid: Double,
    @SerialName("currency") val currency: String,
    @SerialName("date_upd") val dateUpdated: String,
    @SerialName("customer") val customer: OrderCustomerDto,
    @SerialName("items") val items: List<OrderItemDto>? = null,
    @SerialName("shipping") val shipping: OrderShippingDto? = null
)

@Serializable
data class OrderDetailResponseDto(
    @SerialName("order") val order: OrderDto
)

@Serializable
data class OrderCustomerDto(
    @SerialName("firstname") val firstName: String,
    @SerialName("lastname") val lastName: String,
    @SerialName("email") val email: String? = null
)

@Serializable
data class OrderItemDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("name") val name: String,
    @SerialName("quantity") val quantity: Int,
    @SerialName("price_tax_incl") val priceTaxIncl: Double
)

@Serializable
data class OrderShippingDto(
    @SerialName("carrier_name") val carrierName: String,
    @SerialName("tracking_number") val trackingNumber: String?
)
