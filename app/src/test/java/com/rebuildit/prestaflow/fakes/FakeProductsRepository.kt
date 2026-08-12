package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductUpdateFields
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Fake en mémoire de [ProductsRepository].
 *
 * - [setProducts] pousse une nouvelle liste dans le flux.
 * - [refreshCalls] enregistre chaque appel à [refresh] avec ses paramètres.
 * - [refreshTotal] : valeur renvoyée par [refresh] (simule le total API).
 */
class FakeProductsRepository : ProductsRepository {
    private val productsFlowState = MutableStateFlow<List<Product>>(emptyList())

    fun setProducts(products: List<Product>) {
        productsFlowState.value = products
    }

    data class RefreshCall(
        val forceRemote: Boolean,
        val stockFilter: String?,
        val active: String? = null,
        val search: String?,
    )

    val refreshCalls = mutableListOf<RefreshCall>()

    var refreshTotal: Int? = 42
    var shouldThrowOnRefresh = false

    override fun observeProducts(): Flow<List<Product>> = productsFlowState.asStateFlow()

    override fun observeProduct(productId: Long): Flow<Product?> = MutableStateFlow(productsFlowState.value.find { it.id == productId })

    override fun observeStockAvailabilities(productId: Long): Flow<List<StockAvailability>> = MutableStateFlow(emptyList())

    override suspend fun refresh(
        forceRemote: Boolean,
        stockFilter: String?,
        active: String?,
        search: String?,
    ): Int? {
        refreshCalls += RefreshCall(forceRemote, stockFilter, active, search)
        if (shouldThrowOnRefresh) throw RuntimeException("Erreur réseau simulée")
        return refreshTotal
    }

    val countByStockCalls = mutableListOf<String?>()
    var lowStockCountResult: Int? = 7

    // Total catalogue complet (actifs + inactifs), renvoyé quand countByStock est appelé sans
    // filtre (stockFilter = null) — cf. ProductsRepository.countByStock kdoc.
    var catalogCountResult: Int? = 100

    override suspend fun countByStock(stockFilter: String?): Int? {
        countByStockCalls += stockFilter
        return if (stockFilter == null) catalogCountResult else lowStockCountResult
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

    data class SetProductEan13Call(val productId: Long, val ean13: String, val combinationId: Long? = null)

    val setProductEan13Calls = mutableListOf<SetProductEan13Call>()
    var setProductEan13Result: Product? = null
    var shouldThrowOnSetProductEan13 = false

    override suspend fun setProductEan13(
        productId: Long,
        ean13: String,
        combinationId: Long?,
    ): Product {
        setProductEan13Calls += SetProductEan13Call(productId, ean13, combinationId)
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

    data class UpdateStockCall(
        val productId: Long,
        val quantity: Int,
        val warehouseId: Long?,
        val reason: String?,
        val combinationId: Long? = null,
    )

    val updateStockCalls = mutableListOf<UpdateStockCall>()
    var shouldThrowOnUpdateStock = false

    override suspend fun updateStock(
        productId: Long,
        quantity: Int,
        warehouseId: Long?,
        reason: String?,
        combinationId: Long?,
    ) {
        updateStockCalls += UpdateStockCall(productId, quantity, warehouseId, reason, combinationId)
        if (shouldThrowOnUpdateStock) throw RuntimeException("Erreur réseau simulée")
    }

    data class AdjustStockCall(
        val productId: Long,
        val delta: Int,
        val warehouseId: Long?,
        val reason: String?,
        val combinationId: Long? = null,
    )

    val adjustStockCalls = mutableListOf<AdjustStockCall>()

    /** IDs de produit pour lesquels [adjustStock] doit échouer (une entrée = un échec, cf. tests d'échec partiel). */
    val productIdsFailingOnAdjustStock = mutableSetOf<Long>()

    override suspend fun adjustStock(
        productId: Long,
        delta: Int,
        warehouseId: Long?,
        reason: String?,
        combinationId: Long?,
    ) {
        adjustStockCalls += AdjustStockCall(productId, delta, warehouseId, reason, combinationId)
        if (productId in productIdsFailingOnAdjustStock) throw RuntimeException("Erreur réseau simulée")
    }

    data class UploadProductImageCall(val productId: Long, val image: File)

    val uploadProductImageCalls = mutableListOf<UploadProductImageCall>()
    var uploadProductImageResult: Product? = null
    var shouldThrowOnUploadProductImage = false

    override suspend fun uploadProductImage(
        productId: Long,
        image: File,
    ): Product {
        // Délai virtuel : introduit un vrai point de suspension, observable via runCurrent()
        // dans les tests qui vérifient l'état "en cours d'upload".
        delay(1)
        uploadProductImageCalls += UploadProductImageCall(productId, image)
        if (shouldThrowOnUploadProductImage) throw RuntimeException("Erreur réseau simulée")
        return uploadProductImageResult ?: error("uploadProductImageResult non défini dans le fake")
    }

    data class DeleteProductImageCall(val productId: Long, val imageId: Long)

    val deleteProductImageCalls = mutableListOf<DeleteProductImageCall>()
    var deleteProductImageResult: Product? = null
    var shouldThrowOnDeleteProductImage = false

    override suspend fun deleteProductImage(
        productId: Long,
        imageId: Long,
    ): Product {
        // Délai virtuel : introduit un vrai point de suspension, observable via runCurrent()
        // dans les tests qui vérifient l'état "en cours de suppression".
        delay(1)
        deleteProductImageCalls += DeleteProductImageCall(productId, imageId)
        if (shouldThrowOnDeleteProductImage) throw RuntimeException("Erreur réseau simulée")
        return deleteProductImageResult ?: error("deleteProductImageResult non défini dans le fake")
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
