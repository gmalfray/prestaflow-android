package com.rebuildit.prestaflow.domain.products

import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import kotlinx.coroutines.flow.Flow

interface ProductsRepository {
    fun observeProducts(): Flow<List<Product>>
    fun observeProduct(productId: Long): Flow<Product?>
    fun observeStockAvailabilities(productId: Long): Flow<List<StockAvailability>>
    suspend fun refresh(forceRemote: Boolean = false)
    suspend fun refreshProduct(productId: Long, forceRemote: Boolean = false)
    suspend fun updateStock(
        productId: Long,
        quantity: Int,
        warehouseId: Long? = null,
        reason: String? = null
    )
}
