package com.rebuildit.prestaflow.data.products

import androidx.room.withTransaction
import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.local.dao.ProductDao
import com.rebuildit.prestaflow.data.local.dao.StockAvailabilityDao
import com.rebuildit.prestaflow.data.local.db.PrestaFlowDatabase
import com.rebuildit.prestaflow.data.local.entity.StockAvailabilityEntity
import com.rebuildit.prestaflow.data.products.mapper.toDomain
import com.rebuildit.prestaflow.data.products.mapper.toEntity
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.PaginationDto
import com.rebuildit.prestaflow.data.remote.dto.ProductDto
import com.rebuildit.prestaflow.data.remote.dto.ProductUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.StockUpdateRequestDto
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.domain.products.model.ProductUpdateFields
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("LongParameterList") // Repository Hilt : dépendances DAO Room, API, queue sync, mapper, dispatcher et Json toutes nécessaires
class ProductsRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val productDao: ProductDao,
        private val stockAvailabilityDao: StockAvailabilityDao,
        private val database: PrestaFlowDatabase,
        private val syncQueueRepository: SyncQueueRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
        private val json: Json,
        private val endpointManager: ApiEndpointManager,
    ) : ProductsRepository {
        private companion object {
            private const val FETCH_LIMIT = 200
        }

        override fun observeProducts(): Flow<List<Product>> =
            productDao.observeProducts().map { entities ->
                entities.map { it.toDomain() }
            }

        override fun observeProduct(productId: Long): Flow<Product?> = productDao.observeProduct(productId).map { it?.toDomain() }

        override fun observeStockAvailabilities(productId: Long): Flow<List<StockAvailability>> =
            stockAvailabilityDao.observeForProduct(productId).map { entities ->
                entities.map { it.toDomain() }
            }

        override suspend fun refresh(
            forceRemote: Boolean,
            stockFilter: String?,
            active: String?,
            search: String?,
        ): Int? =
            withContext(ioDispatcher) {
                val result = runCatching { fetchAllProducts(stockFilter, active, search) }
                result.fold(
                    onSuccess = { (products, total) ->
                        val productEntities = products.map { it.toEntity() }
                        val stockEntities = products.map { it.stock.toEntity(it.id) }
                        database.withTransaction {
                            productDao.clear()
                            if (productEntities.isNotEmpty()) {
                                productDao.upsertProducts(productEntities)
                                stockAvailabilityDao.upsertAll(stockEntities)
                            }
                        }
                        Timber.d("Products refresh succeeded (count=%d, total=%d)", productEntities.size, total)
                        total
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        if (forceRemote) throw error
                        null
                    },
                )
            }

        override suspend fun countByStock(stockFilter: String?): Int? =
            withContext(ioDispatcher) {
                runCatching {
                    val filters = mutableMapOf("limit" to "1")
                    if (stockFilter != null) {
                        filters["stock"] = stockFilter
                    }
                    val response = api.getProducts(filters)
                    response.total.takeIf { it > 0 } ?: response.pagination?.total ?: 0
                }.getOrElse { error ->
                    Timber.w(networkErrorMapper.map(error).toString())
                    null
                }
            }

        override suspend fun refreshProduct(
            productId: Long,
            forceRemote: Boolean,
        ) {
            withContext(ioDispatcher) {
                val result = runCatching { api.getProduct(productId) }
                result.fold(
                    onSuccess = { payload ->
                        val dto = payload.product
                        productDao.upsertProduct(dto.toEntity())
                        stockAvailabilityDao.upsertAll(listOf(dto.stock.toEntity(dto.id)))
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        if (forceRemote) throw error
                    },
                )
            }
        }

        override suspend fun searchByBarcode(barcode: String): List<Product> =
            withContext(ioDispatcher) {
                val response = api.getProducts(filters = mapOf("barcode" to barcode))
                response.products.map { it.toDomain() }
            }

        override suspend fun searchProducts(query: String): List<Product> =
            withContext(ioDispatcher) {
                val response = api.getProducts(search = query.takeIf { it.isNotBlank() })
                response.products.map { it.toDomain() }
            }

        override suspend fun setProductEan13(
            productId: Long,
            ean13: String,
            combinationId: Long?,
        ): Product =
            withContext(ioDispatcher) {
                val response =
                    api.updateProduct(productId, ProductUpdateRequestDto(ean13 = ean13, combinationId = combinationId))
                val dto = response.product
                // Le produit vient d'une recherche texte transitoire (pas forcément en cache) :
                // on le met en cache maintenant qu'on dispose de la version serveur à jour.
                productDao.upsertProduct(dto.toEntity())
                stockAvailabilityDao.upsertAll(listOf(dto.stock.toEntity(dto.id)))
                dto.toDomain()
            }

        override suspend fun updateProductFields(
            productId: Long,
            fields: ProductUpdateFields,
        ): Product =
            withContext(ioDispatcher) {
                val request =
                    ProductUpdateRequestDto(
                        name = fields.name,
                        description = fields.description,
                        descriptionShort = fields.descriptionShort,
                        reference = fields.reference,
                        priceTaxExcl = fields.priceTaxExcl,
                        active = fields.active,
                    )
                val response = api.updateProduct(productId, request)
                val dto = response.product
                productDao.upsertProduct(dto.toEntity())
                stockAvailabilityDao.upsertAll(listOf(dto.stock.toEntity(dto.id)))
                dto.toDomain()
            }

        override suspend fun uploadProductImage(
            productId: Long,
            image: File,
        ): Product =
            withContext(ioDispatcher) {
                val requestBody = image.asRequestBody("image/jpeg".toMediaType())
                val part = MultipartBody.Part.createFormData("image", image.name, requestBody)
                val response = api.uploadProductImage(productId, part)
                val dto = response.product
                productDao.upsertProduct(dto.toEntity())
                stockAvailabilityDao.upsertAll(listOf(dto.stock.toEntity(dto.id)))
                dto.toDomain()
            }

        override suspend fun deleteProductImage(
            productId: Long,
            imageId: Long,
        ): Product =
            withContext(ioDispatcher) {
                val response = api.deleteProductImage(productId, imageId)
                val dto = response.product
                productDao.upsertProduct(dto.toEntity())
                stockAvailabilityDao.upsertAll(listOf(dto.stock.toEntity(dto.id)))
                dto.toDomain()
            }

        override suspend fun updatePrice(
            productId: Long,
            price: Double,
        ) {
            withContext(ioDispatcher) {
                productDao.getById(productId)?.let { existing ->
                    productDao.upsertProduct(
                        existing.copy(
                            price = price,
                            updatedAt = java.time.Instant.now().toString(),
                        ),
                    )
                }
            }
        }

        override suspend fun updateStatus(
            productId: Long,
            active: Boolean,
        ) {
            withContext(ioDispatcher) {
                productDao.getById(productId)?.let { existing ->
                    productDao.upsertProduct(
                        existing.copy(
                            active = active,
                            updatedAt = java.time.Instant.now().toString(),
                        ),
                    )
                }
            }
        }

        override suspend fun updateStock(
            productId: Long,
            quantity: Int,
            warehouseId: Long?,
            reason: String?,
            combinationId: Long?,
        ) {
            withContext(ioDispatcher) {
                val now = java.time.Instant.now().toString()
                val normalizedWarehouseId = warehouseId ?: StockAvailabilityEntity.NO_WAREHOUSE_ID

                // Mise à jour optimiste LOCALE uniquement si le produit est déjà en cache Room ET
                // que l'ajustement concerne le produit lui-même (pas une combinaison : Room ne
                // modélise pas le stock par combinaison, écrire `quantity` sur le produit parent
                // corromprait son stock affiché). Cas scan code-barres : le produit vient souvent
                // d'un lookup transitoire (pas en cache) → l'écriture stock_availability échouerait
                // sur la FK vers `products`. On saute alors l'écriture locale et on laisse l'API
                // faire foi (la liste Produits se rafraîchira).
                if (combinationId == null) {
                    productDao.getById(productId)?.let { existing ->
                        val updatedStock =
                            json.decodeFromString<ProductStock>(existing.stockJson).copy(
                                quantity = quantity,
                                updatedAt = now,
                            )
                        productDao.upsertProduct(
                            existing.copy(
                                stockJson = json.encodeToString(updatedStock),
                                updatedAt = now,
                            ),
                        )
                        stockAvailabilityDao.upsertAll(
                            listOf(
                                StockAvailabilityEntity(
                                    productId = productId,
                                    warehouseId = normalizedWarehouseId,
                                    quantity = quantity,
                                    updatedAtIso = now,
                                ),
                            ),
                        )
                    }
                }

                val request =
                    StockUpdateRequestDto(
                        quantity = quantity,
                        warehouseId = warehouseId,
                        reason = reason,
                        combinationId = combinationId,
                    )
                val payloadJson = json.encodeToString(request)
                val endpoint = "products/$productId/stock"

                val result = runCatching { api.updateProductStock(productId, request) }
                result.fold(
                    onSuccess = { Timber.d("Stock updated for product $productId") },
                    onFailure = { error ->
                        Timber.w(error, "Failed to update stock remotely, enqueuing task")
                        syncQueueRepository.enqueue(
                            endpoint = endpoint,
                            method = "PATCH",
                            payloadJson = payloadJson,
                            // Boutique active au moment de l'échec, figée sur la tâche (cf. FIX
                            // "file offline rejouée contre la mauvaise boutique").
                            shopUrl = endpointManager.getStoredShopUrl().orEmpty(),
                            resourceType = "product",
                            resourceId = productId,
                            conflictStrategy = ConflictStrategy.MERGE,
                        )
                        Timber.w(networkErrorMapper.map(error).toString())
                    },
                )
            }
        }

        override suspend fun adjustStock(
            productId: Long,
            delta: Int,
            warehouseId: Long?,
            reason: String?,
            combinationId: Long?,
        ) {
            withContext(ioDispatcher) {
                val now = java.time.Instant.now().toString()
                val normalizedWarehouseId = warehouseId ?: StockAvailabilityEntity.NO_WAREHOUSE_ID

                // Mise à jour optimiste LOCALE, même garde que updateStock (cf. son commentaire) —
                // ICI on incrémente la quantité en cache plutôt que de l'écraser, cohérent avec le
                // mode relatif.
                if (combinationId == null) {
                    productDao.getById(productId)?.let { existing ->
                        val currentStock = json.decodeFromString<ProductStock>(existing.stockJson)
                        val updatedStock = currentStock.copy(quantity = currentStock.quantity + delta, updatedAt = now)
                        productDao.upsertProduct(
                            existing.copy(
                                stockJson = json.encodeToString(updatedStock),
                                updatedAt = now,
                            ),
                        )
                        stockAvailabilityDao.upsertAll(
                            listOf(
                                StockAvailabilityEntity(
                                    productId = productId,
                                    warehouseId = normalizedWarehouseId,
                                    quantity = updatedStock.quantity,
                                    updatedAtIso = now,
                                ),
                            ),
                        )
                    }
                }

                val request =
                    StockUpdateRequestDto(
                        delta = delta,
                        warehouseId = warehouseId,
                        reason = reason,
                        combinationId = combinationId,
                    )
                // Pas d'enfilement offline ici (contrairement à updateStock) : le journal de réappro
                // (ReplenishSessionRepository) sert DÉJÀ de file rejouable pour cette écriture — la
                // ligne reste en attente côté journal en cas d'échec, l'utilisateur la revalide
                // explicitement. Enfiler en plus créerait un double envoi au retour du réseau.
                // L'exception est donc PROPAGÉE (pas de runCatching ici) pour que l'appelant sache
                // que CETTE ligne précise a échoué.
                api.updateProductStock(productId, request)
                Timber.d("Stock adjusted (delta=$delta) for product $productId")
            }
        }

        /**
         * Récupère toutes les pages de produits depuis l'API.
         * @return Paire (liste complète des produits, total réel selon filtres/recherche).
         *   Le total provient du champ `total` de la première réponse ; il reflète le vrai
         *   nombre de produits correspondant aux critères actifs côté serveur.
         */
        private suspend fun fetchAllProducts(
            stockFilter: String? = null,
            active: String? = null,
            search: String? = null,
        ): Pair<List<ProductDto>, Int> {
            val collected = mutableListOf<ProductDto>()
            var offset = 0
            var hasNext = true
            var reportedTotal = 0
            while (hasNext) {
                val filters = buildProductFilters(offset, stockFilter, active)
                val response = api.getProducts(filters, search = search?.takeIf { it.isNotBlank() })
                if (collected.isEmpty()) {
                    // Le total de la première page est le vrai total (filtres + recherche actifs)
                    reportedTotal = response.total.takeIf { it > 0 }
                        ?: response.pagination?.total
                        ?: 0
                }
                if (response.products.isEmpty()) {
                    Timber.d("Products page fetched with no items, stopping iteration (offset=%d)", offset)
                    break
                }
                collected += response.products

                val advance = advancePage(response.pagination, offset, response.products.size)
                hasNext = advance.hasNext
                offset = advance.nextOffset
            }
            // Fallback : si le backend n'a pas renvoyé de total, on utilise le nombre réel collecté
            if (reportedTotal == 0 && collected.isNotEmpty()) reportedTotal = collected.size
            return collected to reportedTotal
        }

        /**
         * Construit les paramètres de requête `GET products` pour une page donnée. `stockFilter` et
         * `active` ne sont JAMAIS combinés côté appelant (cf. [com.rebuildit.prestaflow.domain.products.model.StockFilter]),
         * mais cette fonction reste défensive et ajoute chacun indépendamment s'il est fourni.
         */
        private fun buildProductFilters(
            offset: Int,
            stockFilter: String?,
            active: String?,
        ): MutableMap<String, String> {
            val filters = mutableMapOf("limit" to FETCH_LIMIT.toString())
            if (offset > 0) {
                filters["offset"] = offset.toString()
            }
            if (stockFilter != null) {
                filters["stock"] = stockFilter
            }
            if (active != null) {
                filters["active"] = active
            }
            return filters
        }

        private data class PageAdvance(val nextOffset: Int, val hasNext: Boolean)

        /** Calcule le prochain offset et si une page suivante doit être chargée, à partir de la pagination renvoyée. */
        private fun advancePage(
            pagination: PaginationDto?,
            offset: Int,
            fetchedCount: Int,
        ): PageAdvance {
            val pageCount = pagination?.count ?: fetchedCount
            val nextOffset =
                when {
                    pagination?.offset != null -> pagination.offset + pageCount
                    pageCount > 0 -> offset + pageCount
                    else -> offset
                }
            Timber.d(
                "Products page fetched (offset=%d, count=%d, hasNext=%s, nextOffset=%d)",
                pagination?.offset ?: offset,
                pageCount,
                pagination?.hasNext,
                nextOffset,
            )
            val hasNext = pagination?.hasNext == true && pageCount > 0 && nextOffset > offset
            return PageAdvance(nextOffset, hasNext)
        }
    }
