package com.rebuildit.prestaflow.domain.products.model

data class Product(
    val id: Long,
    val name: String,
    val sku: String?,
    val priceTaxIncl: Double,
    val active: Boolean,
    val stockQuantity: Int,
    val imageUrl: String?,
    val lastUpdatedIso: String?
)
