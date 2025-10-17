package com.rebuildit.prestaflow.data.orders

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.local.dao.OrderDao
import com.rebuildit.prestaflow.data.orders.mapper.toDomain
import com.rebuildit.prestaflow.data.orders.mapper.toEntity
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class OrdersRepositoryImpl @Inject constructor(
    private val api: PrestaFlowApi,
    private val orderDao: OrderDao,
    private val networkErrorMapper: NetworkErrorMapper,
    private val ioDispatcher: CoroutineDispatcher
) : OrdersRepository {

    override fun observeOrders(): Flow<List<Order>> = orderDao.observeOrders().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun refresh(forceRemote: Boolean) {
        withContext(ioDispatcher) {
            val filters = mapOf("sort" to "-date_add", "limit" to "50")
            val result = runCatching { api.getOrders(filters) }
            result.fold(
                onSuccess = { payload ->
                    val entities = payload.orders.map { it.toEntity() }
                    orderDao.upsertOrders(entities)
                },
                onFailure = { error ->
                    Timber.w(networkErrorMapper.map(error).toString())
                    if (forceRemote) throw error
                }
            )
        }
    }
}
