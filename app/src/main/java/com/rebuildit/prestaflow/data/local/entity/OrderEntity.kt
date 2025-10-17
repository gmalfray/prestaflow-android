package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Long,
    val reference: String,
    val status: String,
    @ColumnInfo(name = "total_paid") val totalPaid: Double,
    val currency: String,
    @ColumnInfo(name = "customer_name") val customerName: String,
    @ColumnInfo(name = "updated_at_iso") val updatedAtIso: String
)
