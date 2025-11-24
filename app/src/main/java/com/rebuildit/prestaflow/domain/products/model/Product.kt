package com.rebuildit.prestaflow.domain.products.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: Long,
    val name: String,
    val reference: String,
    val price: Double,
    val active: Boolean,
    val stock: ProductStock,
    val images: List<ProductImage>,
    val updatedAt: String
)

@Serializable
data class ProductStock(
    val quantity: Int,
    val warehouseId: Long? = null,
    val updatedAt: String? = null
)

@Serializable
data class ProductImage(
    val id: Long,
    val url: String
)
