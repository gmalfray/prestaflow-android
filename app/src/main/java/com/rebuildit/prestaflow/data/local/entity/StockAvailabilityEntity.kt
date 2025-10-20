package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "stock_availabilities",
    primaryKeys = ["product_id", "warehouse_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("product_id"),
        Index("warehouse_id")
    ]
)
data class StockAvailabilityEntity(
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "warehouse_id") val warehouseId: Long,
    val quantity: Int,
    @ColumnInfo(name = "updated_at_iso") val updatedAtIso: String?
) {
    companion object {
        const val NO_WAREHOUSE_ID: Long = -1L
    }
}
