package com.rebuildit.prestaflow.data.orders

import android.content.Context
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.local.dao.OrderDao
import com.rebuildit.prestaflow.data.orders.mapper.toDomain
import com.rebuildit.prestaflow.data.orders.mapper.toEntity
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.DeviceRegistrationRequestDto
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : OrdersRepository {

    override fun observeOrders(): Flow<List<Order>> = orderDao.observeOrders().map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getOrder(orderId: Long): Flow<Order?> = orderDao.observeOrder(orderId).map { entity ->
        entity?.toDomain()
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
                }
            )
        }
    }

    override suspend fun registerToken(token: String) {
        withContext(ioDispatcher) {
            runCatching {
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                
                api.registerPushToken(
                    DeviceRegistrationRequestDto(
                        token = token,
                        deviceId = deviceId,
                        platform = "android"
                    )
                )
            }.onFailure { error ->
                Timber.w(networkErrorMapper.map(error).toString())
                // Don't throw, just log warning for push registration failure
            }
        }
    }
}
