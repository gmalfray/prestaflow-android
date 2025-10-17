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
    @SerialName("customer") val customer: OrderCustomerDto
)

@Serializable
data class OrderCustomerDto(
    @SerialName("firstname") val firstName: String,
    @SerialName("lastname") val lastName: String
)
