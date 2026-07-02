package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductUpdateFields
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

    override fun observeProduct(productId: Long): Flow<Product?> = MutableStateFlow(_productsFlow.value.find { it.id == productId })

    override fun observeStockAvailabilities(productId: Long): Flow<List<StockAvailability>> = MutableStateFlow(emptyList())

    override suspend fun refresh(
        forceRemote: Boolean,
        stockFilter: String?,
        search: String?,
    ): Int? {
        refreshCalls += RefreshCall(forceRemote, stockFilter, search)
        if (shouldThrowOnRefresh) throw RuntimeException("Erreur réseau simulée")
        return refreshTotal
    }

    override suspend fun refreshProduct(
        productId: Long,
        forceRemote: Boolean,
    ) = Unit

    val barcodeSearchCalls = mutableListOf<String>()
    var barcodeSearchResult: List<Product> = emptyList()
    var shouldThrowOnBarcodeSearch = false

    override suspend fun searchByBarcode(barcode: String): List<Product> {
        barcodeSearchCalls += barcode
        if (shouldThrowOnBarcodeSearch) throw RuntimeException("Erreur réseau simulée")
        return barcodeSearchResult
    }

    val searchProductsCalls = mutableListOf<String>()
    var searchProductsResult: List<Product> = emptyList()
    var shouldThrowOnSearchProducts = false

    override suspend fun searchProducts(query: String): List<Product> {
        searchProductsCalls += query
        if (shouldThrowOnSearchProducts) throw RuntimeException("Erreur réseau simulée")
        return searchProductsResult
    }

    data class SetProductEan13Call(val productId: Long, val ean13: String)

    val setProductEan13Calls = mutableListOf<SetProductEan13Call>()
    var setProductEan13Result: Product? = null
    var shouldThrowOnSetProductEan13 = false

    override suspend fun setProductEan13(
        productId: Long,
        ean13: String,
    ): Product {
        setProductEan13Calls += SetProductEan13Call(productId, ean13)
        if (shouldThrowOnSetProductEan13) throw RuntimeException("Erreur réseau simulée")
        return setProductEan13Result ?: error("setProductEan13Result non défini dans le fake")
    }

    data class UpdateProductFieldsCall(val productId: Long, val fields: ProductUpdateFields)

    val updateProductFieldsCalls = mutableListOf<UpdateProductFieldsCall>()
    var updateProductFieldsResult: Product? = null
    var shouldThrowOnUpdateProductFields = false

    override suspend fun updateProductFields(
        productId: Long,
        fields: ProductUpdateFields,
    ): Product {
        updateProductFieldsCalls += UpdateProductFieldsCall(productId, fields)
        if (shouldThrowOnUpdateProductFields) throw RuntimeException("Erreur réseau simulée")
        return updateProductFieldsResult ?: error("updateProductFieldsResult non défini dans le fake")
    }

    data class UpdateStockCall(val productId: Long, val quantity: Int, val warehouseId: Long?, val reason: String?)

    val updateStockCalls = mutableListOf<UpdateStockCall>()
    var shouldThrowOnUpdateStock = false

    override suspend fun updateStock(
        productId: Long,
        quantity: Int,
        warehouseId: Long?,
        reason: String?,
    ) {
        updateStockCalls += UpdateStockCall(productId, quantity, warehouseId, reason)
        if (shouldThrowOnUpdateStock) throw RuntimeException("Erreur réseau simulée")
    }

    override suspend fun updatePrice(
        productId: Long,
        price: Double,
    ) = Unit

    override suspend fun updateStatus(
        productId: Long,
        active: Boolean,
    ) = Unit
}
