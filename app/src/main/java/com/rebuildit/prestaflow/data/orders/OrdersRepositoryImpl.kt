package com.rebuildit.prestaflow.data.orders

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.local.dao.OrderDao
import com.rebuildit.prestaflow.data.orders.mapper.toDomain
import com.rebuildit.prestaflow.data.orders.mapper.toEntity
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.OrderShippingUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.OrderStatusUpdateRequestDto
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrdersRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val orderDao: OrderDao,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
    ) : OrdersRepository {
        override fun observeOrders(): Flow<List<Order>> =
            orderDao.observeOrders().map { entities ->
                entities.map { it.toDomain() }
            }

        override fun getOrder(orderId: Long): Flow<Order?> =
            orderDao.observeOrder(orderId).map { entity ->
                entity?.toDomain()
            }

        override suspend fun getOrderStatuses(): List<OrderStatusFilter> =
            withContext(ioDispatcher) {
                val response = api.getOrderStatuses()
                response.statuses.map { dto ->
                    OrderStatusFilter(id = dto.id, name = dto.name, color = dto.color)
                }
            }

        override suspend fun refresh(
            forceRemote: Boolean,
            statusId: Int?,
        ) {
            withContext(ioDispatcher) {
                val filters =
                    buildMap {
                        put("sort", "-date_add")
                        put("limit", "50")
                        if (statusId != null) put("status", statusId.toString())
                    }
                val result = runCatching { api.getOrders(filters) }
                result.fold(
                    onSuccess = { payload ->
                        val entities = payload.orders.map { it.toEntity() }
                        orderDao.upsertOrders(entities)
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        if (forceRemote) throw error
                    },
                )
            }
        }

        override suspend fun refreshOrder(orderId: Long) {
            withContext(ioDispatcher) {
                val result = runCatching { api.getOrder(orderId) }
                result.fold(
                    onSuccess = { payload ->
                        val entity = payload.order.toEntity()
                        orderDao.upsertOrders(listOf(entity))
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    },
                )
            }
        }

        override suspend fun updateOrderStatus(
            orderId: Long,
            status: String,
        ) {
            withContext(ioDispatcher) {
                runCatching {
                    api.updateOrderStatus(orderId, OrderStatusUpdateRequestDto(status = status))
                }.fold(
                    onSuccess = { refreshOrder(orderId) },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    },
                )
            }
        }

        override suspend fun updateOrderShipping(
            orderId: Long,
            trackingNumber: String,
            carrierId: Long?,
        ) {
            withContext(ioDispatcher) {
                runCatching {
                    api.updateOrderShipping(
                        orderId,
                        OrderShippingUpdateRequestDto(
                            trackingNumber = trackingNumber,
                            carrierId = carrierId,
                        ),
                    )
                }.fold(
                    onSuccess = { refreshOrder(orderId) },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    },
                )
            }
        }

        override suspend fun downloadInvoicePdf(orderId: Long): ByteArray? =
            withContext(ioDispatcher) {
                val response = api.getInvoicePdf(orderId)
                when {
                    response.isSuccessful -> response.body()?.bytes()
                    response.code() == 404 -> null
                    else -> {
                        val msg = "Erreur HTTP ${response.code()} lors du téléchargement de la facture #$orderId"
                        Timber.w(msg)
                        error(msg)
                    }
                }
            }
    }
