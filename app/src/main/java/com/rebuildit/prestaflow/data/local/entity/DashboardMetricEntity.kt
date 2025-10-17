package com.rebuildit.prestaflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_metrics")
data class DashboardMetricEntity(
    @PrimaryKey val period: String,
    val turnover: Double,
    val ordersCount: Int,
    val customersCount: Int,
    val productsCount: Int,
    val lastUpdatedIso: String
)
