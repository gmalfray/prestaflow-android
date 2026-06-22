package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The connector exposes two distinct order shapes:
 *  - the list endpoint (`OrdersService::formatOrderRow`) returns a FLAT row
 *    with `status` as a string and `total_paid` / `currency` / `date_upd`.
 *  - the detail endpoint (`OrdersService::getOrderById`) returns a NESTED
 *    object with `status` / `totals` / `customer` / `shipping` / `dates`
 *    / `items` / `history`.
 */

@Serializable
data class OrderListDto(
    @SerialName("orders") val orders: List<OrderListItemDto>,
)

@Serializable
data class OrderListItemDto(
    @SerialName("id") val id: Long,
    @SerialName("reference") val reference: String,
    @SerialName("status") val status: String = "",
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    @SerialName("currency") val currency: String = "",
    @SerialName("date_upd") val dateUpdated: String? = null,
    @SerialName("customer") val customer: OrderListCustomerDto,
)

@Serializable
data class OrderListCustomerDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("firstname") val firstName: String = "",
    @SerialName("lastname") val lastName: String = "",
)

@Serializable
data class OrderDetailResponseDto(
    @SerialName("order") val order: OrderDto,
)

@Serializable
data class OrderDto(
    @SerialName("id") val id: Long,
    @SerialName("reference") val reference: String,
    @SerialName("status") val status: OrderStatusDto,
    @SerialName("totals") val totals: OrderTotalsDto? = null,
    @SerialName("customer") val customer: OrderCustomerDto,
    @SerialName("shipping") val shipping: OrderShippingDto? = null,
    @SerialName("dates") val dates: OrderDatesDto? = null,
    @SerialName("items") val items: List<OrderItemDto>? = null,
    @SerialName("history") val history: List<OrderHistoryDto>? = null,
)

@Serializable
data class OrderStatusDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class OrderTotalsDto(
    @SerialName("paid_tax_incl") val paidTaxIncl: Double = 0.0,
    @SerialName("paid_tax_excl") val paidTaxExcl: Double = 0.0,
    @SerialName("currency") val currency: String? = null,
)

@Serializable
data class OrderCustomerDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("firstname") val firstName: String = "",
    @SerialName("lastname") val lastName: String = "",
    @SerialName("email") val email: String? = null,
)

@Serializable
data class OrderDatesDto(
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class OrderItemDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("name") val name: String,
    @SerialName("reference") val reference: String? = null,
    @SerialName("quantity") val quantity: Int,
    @SerialName("price_tax_incl") val priceTaxIncl: Double,
    @SerialName("price_tax_excl") val priceTaxExcl: Double = 0.0,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class OrderShippingDto(
    @SerialName("carrier_id") val carrierId: Long = 0,
    @SerialName("carrier_name") val carrierName: String = "",
    @SerialName("tracking_number") val trackingNumber: String? = null,
)

@Serializable
data class OrderHistoryDto(
    @SerialName("order_state_id") val orderStateId: Long = 0,
    @SerialName("status") val status: String = "",
    @SerialName("date_add") val dateAdd: String? = null,
)

@Serializable
data class OrderStatusUpdateRequestDto(
    @SerialName("status") val status: String,
)

@Serializable
data class OrderShippingUpdateRequestDto(
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("carrier_id") val carrierId: Long? = null,
)
