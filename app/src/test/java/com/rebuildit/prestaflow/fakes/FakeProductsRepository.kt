package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake en mémoire de [ProductsRepository].
 *
 * - [setProducts] pousse une nouvelle liste dans le flux.
 * - [refreshCalls] enregistre chaque appel à [refresh] avec ses paramètres.
 * - [refreshTotal] : valeur renvoyée par [refresh] (simule le total API).
 */
class FakeProductsRepository : ProductsRepository {

    private val _productsFlow = MutableStateFlow<List<Product>>(emptyList())

    fun setProducts(products: List<Product>) {
        _productsFlow.value = products
    }

    data class RefreshCall(val forceRemote: Boolean, val stockFilter: String?, val search: String?)

    val refreshCalls = mutableListOf<RefreshCall>()

    var refreshTotal: Int? = 42
    var shouldThrowOnRefresh = false

    override fun observeProducts(): Flow<List<Product>> = _productsFlow.asStateFlow()

    override fun observeProduct(productId: Long): Flow<Product?> =
        MutableStateFlow(_productsFlow.value.find { it.id == productId })

    override fun observeStockAvailabilities(productId: Long): Flow<List<StockAvailability>> =
        MutableStateFlow(emptyList())

    override suspend fun refresh(forceRemote: Boolean, stockFilter: String?, search: String?): Int? {
        refreshCalls += RefreshCall(forceRemote, stockFilter, search)
        if (shouldThrowOnRefresh) throw RuntimeException("Erreur réseau simulée")
        return refreshTotal
    }

    override suspend fun refreshProduct(productId: Long, forceRemote: Boolean) = Unit

    override suspend fun updateStock(productId: Long, quantity: Int, warehouseId: Long?, reason: String?) = Unit

    override suspend fun updatePrice(productId: Long, price: Double) = Unit

    override suspend fun updateStatus(productId: Long, active: Boolean) = Unit
}
