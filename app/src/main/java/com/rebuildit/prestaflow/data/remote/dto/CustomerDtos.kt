package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerListResponseDto(
    @SerialName("customers") val customers: List<CustomerDto>,
    @SerialName("pagination") val pagination: PaginationDto? = null
)

@Serializable
data class CustomerDto(
    @SerialName("id") val id: Long,
    @SerialName("firstname") val firstName: String,
    @SerialName("lastname") val lastName: String,
    @SerialName("email") val email: String,
    @SerialName("orders_count") val ordersCount: Int,
    @SerialName("total_spent") val totalSpent: Double,
    @SerialName("last_order_at") val lastOrderAt: String? = null
)

@Serializable
data class CustomerDetailResponseDto(
    @SerialName("customer") val customer: CustomerDetailDto
)

@Serializable
data class CustomerDetailDto(
    @SerialName("id") val id: Long,
    @SerialName("firstname") val firstName: String,
    @SerialName("lastname") val lastName: String,
    @SerialName("email") val email: String,
    @SerialName("orders_count") val ordersCount: Int,
    @SerialName("total_spent") val totalSpent: Double,
    @SerialName("last_order_at") val lastOrderAt: String? = null,
    @SerialName("orders") val orders: List<CustomerOrderDto> = emptyList()
)

@Serializable
data class CustomerOrderDto(
    @SerialName("id") val id: Long,
    @SerialName("reference") val reference: String,
    @SerialName("status") val status: String,
    @SerialName("total_paid") val totalPaid: Double,
    @SerialName("currency") val currency: String,
    @SerialName("date_add") val dateAdded: String
)
