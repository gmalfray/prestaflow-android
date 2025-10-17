package com.rebuildit.prestaflow.domain.products.model

data class StockAvailability(
    val productId: Long,
    val warehouseId: Long?,
    val quantity: Int,
    val updatedAtIso: String?
)
