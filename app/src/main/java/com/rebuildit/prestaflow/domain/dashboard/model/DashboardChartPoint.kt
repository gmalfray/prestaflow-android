package com.rebuildit.prestaflow.domain.dashboard.model

data class DashboardChartPoint(
    val label: String,
    val orders: Int,
    val customers: Int,
    val turnover: Double
)
