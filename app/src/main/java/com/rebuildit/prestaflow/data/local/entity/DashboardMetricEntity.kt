package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_metrics")
data class DashboardMetricEntity(
    @PrimaryKey val period: String,
    val turnover: Double,
    val ordersCount: Int,
    val customersCount: Int,
    val productsCount: Int,
    val lastUpdatedIso: String,
    @ColumnInfo(name = "chart_json") val chartJson: String,
    /** CA période précédente — null si le connecteur ne le fournit pas encore. */
    @ColumnInfo(name = "previous_turnover", defaultValue = "NULL") val previousTurnover: Double? = null,
)
