package com.rebuildit.prestaflow.domain.products

import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import kotlinx.coroutines.flow.Flow

interface ProductsRepository {
    fun observeProducts(): Flow<List<Product>>

    fun observeProduct(productId: Long): Flow<Product?>

    fun observeStockAvailabilities(productId: Long): Flow<List<StockAvailability>>

    /**
     * Rafraîchit la liste des produits depuis le serveur.
     * @param stockFilter Si non nul, filtre par état de stock : "in_stock", "out_of_stock" ou "low_stock".
     * @param search Si non nul, délègue la recherche textuelle à l'API (nom, référence).
     * @return Le total réel de produits rapporté par l'API (selon filtres actifs), ou null si la requête échoue.
     */
    suspend fun refresh(
        forceRemote: Boolean = false,
        stockFilter: String? = null,
        search: String? = null,
    ): Int?

    suspend fun refreshProduct(
        productId: Long,
        forceRemote: Boolean = false,
    )

    suspend fun updateStock(
        productId: Long,
        quantity: Int,
        warehouseId: Long? = null,
        reason: String? = null,
    )

    suspend fun updatePrice(
        productId: Long,
        price: Double,
    )

    suspend fun updateStatus(
        productId: Long,
        active: Boolean,
    )
}
