package com.rebuildit.prestaflow.domain.dashboard.model

data class DashboardChartPoint(
    val label: String,
    val orders: Int,
    val customers: Int,
    val turnover: Double,
    /** Nouveaux clients inscrits dans le bucket. 0 si le connecteur < v1.6. */
    val newCustomers: Int = 0,
)
