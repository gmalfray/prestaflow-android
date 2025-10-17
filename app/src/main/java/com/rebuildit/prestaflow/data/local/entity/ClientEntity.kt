package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    val email: String,
    @ColumnInfo(name = "orders_count") val ordersCount: Int,
    @ColumnInfo(name = "total_spent") val totalSpent: Double,
    @ColumnInfo(name = "last_order_iso") val lastOrderIso: String?,
    @ColumnInfo(name = "last_synced_iso") val lastSyncedIso: String
)
