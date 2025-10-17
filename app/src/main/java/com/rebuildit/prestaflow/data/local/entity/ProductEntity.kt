package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val sku: String?,
    @ColumnInfo(name = "price_tax_incl") val priceTaxIncl: Double,
    val active: Boolean,
    @ColumnInfo(name = "stock_quantity") val stockQuantity: Int,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "last_updated_iso") val lastUpdatedIso: String?
)
