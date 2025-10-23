package com.rebuildit.prestaflow.data.products

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.local.dao.ProductDao
import com.rebuildit.prestaflow.data.local.dao.StockAvailabilityDao
import com.rebuildit.prestaflow.data.local.entity.StockAvailabilityEntity
import com.rebuildit.prestaflow.data.products.mapper.toDomain
import com.rebuildit.prestaflow.data.products.mapper.toEntity
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.StockUpdateRequestDto
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class ProductsRepositoryImpl @Inject constructor(
    private val api: PrestaFlowApi,
    private val productDao: ProductDao,
    private val stockAvailabilityDao: StockAvailabilityDao,
    private val syncQueueRepository: SyncQueueRepository,
    private val networkErrorMapper: NetworkErrorMapper,
    private val ioDispatcher: CoroutineDispatcher,
    private val json: Json
) : ProductsRepository {

    private companion object {
        private const val FETCH_LIMIT = 200
    }

    override fun observeProducts(): Flow<List<Product>> =
        productDao.observeProducts().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeProduct(productId: Long): Flow<Product?> =
        productDao.observeProduct(productId).map { it?.toDomain() }

    override fun observeStockAvailabilities(productId: Long): Flow<List<StockAvailability>> =
        stockAvailabilityDao.observeForProduct(productId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun refresh(forceRemote: Boolean) {
        withContext(ioDispatcher) {
            val result = runCatching {
                api.getProducts(
                    mapOf("limit" to FETCH_LIMIT.toString())
                )
            }
            result.fold(
                onSuccess = { payload ->
                    val productEntities = payload.products.map { it.toEntity() }
                    productDao.upsertProducts(productEntities)
                    val stockEntities = payload.products.map { it.stock.toEntity(it.id) }
                    stockAvailabilityDao.upsertAll(stockEntities)
                },
                onFailure = { error ->
                    Timber.w(networkErrorMapper.map(error).toString())
                    if (forceRemote) throw error
                }
            )
        }
    }

    override suspend fun refreshProduct(productId: Long, forceRemote: Boolean) {
        withContext(ioDispatcher) {
            val filters = mapOf(
                "id" to productId.toString(),
                "limit" to FETCH_LIMIT.toString()
            )
            val result = runCatching { api.getProducts(filters) }
            result.fold(
                onSuccess = { payload ->
                    payload.products.firstOrNull()?.let { dto ->
                        productDao.upsertProduct(dto.toEntity())
                        stockAvailabilityDao.upsertAll(listOf(dto.stock.toEntity(dto.id)))
                    }
                },
                onFailure = { error ->
                    Timber.w(networkErrorMapper.map(error).toString())
                    if (forceRemote) throw error
                }
            )
        }
    }

    override suspend fun updateStock(
        productId: Long,
        quantity: Int,
        warehouseId: Long?,
        reason: String?
    ) {
        withContext(ioDispatcher) {
            val now = java.time.Instant.now().toString()
            val normalizedWarehouseId = warehouseId ?: StockAvailabilityEntity.NO_WAREHOUSE_ID

            productDao.getById(productId)?.let { existing ->
                productDao.upsertProduct(
                    existing.copy(stockQuantity = quantity, lastUpdatedIso = now)
                )
            }

            stockAvailabilityDao.upsertAll(
                listOf(
                    StockAvailabilityEntity(
                        productId = productId,
                        warehouseId = normalizedWarehouseId,
                        quantity = quantity,
                        updatedAtIso = now
                    )
                )
            )

            val request = StockUpdateRequestDto(
                quantity = quantity,
                warehouseId = warehouseId,
                reason = reason
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
                        resourceType = "product",
                        resourceId = productId,
                        conflictStrategy = ConflictStrategy.MERGE
                    )
                    Timber.w(networkErrorMapper.map(error).toString())
                }
            )
        }
    }
}
