package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val reference: String,
    val price: Double,
    val active: Boolean,
    @ColumnInfo(name = "stock_json") val stockJson: String,
    @ColumnInfo(name = "images_json") val imagesJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)
