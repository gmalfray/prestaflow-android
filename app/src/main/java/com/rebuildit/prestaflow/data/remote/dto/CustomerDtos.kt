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
