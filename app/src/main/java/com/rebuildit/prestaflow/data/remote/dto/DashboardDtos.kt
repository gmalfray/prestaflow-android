package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DashboardMetricsDto(
    @SerialName("turnover") val turnover: Double,
    @SerialName("orders_count") val ordersCount: Int,
    @SerialName("customers_count") val customersCount: Int,
    @SerialName("products_count") val productsCount: Int,
    @SerialName("chart") val chart: List<ChartPointDto>,
    /**
     * CA de la période précédente équivalente, retourné par le connecteur v1.5.x+.
     * Null si le connecteur ne supporte pas encore ce champ.
     */
    @SerialName("previous_turnover") val previousTurnover: Double? = null,
)

@Serializable
data class ChartPointDto(
    @SerialName("label") val label: String,
    @SerialName("orders") val orders: Int,
    @SerialName("customers") val customers: Int,
    @SerialName("turnover") val turnover: Double,
)
