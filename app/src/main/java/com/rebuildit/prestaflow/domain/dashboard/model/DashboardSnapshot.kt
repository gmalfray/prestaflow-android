package com.rebuildit.prestaflow.domain.dashboard.model

data class DashboardSnapshot(
    val period: DashboardPeriod,
    val turnover: Double,
    val ordersCount: Int,
    val customersCount: Int,
    val productsCount: Int,
    val chart: List<DashboardChartPoint>,
    val lastUpdatedIso: String
)
