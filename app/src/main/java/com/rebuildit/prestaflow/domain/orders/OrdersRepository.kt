package com.rebuildit.prestaflow.domain.orders

import com.rebuildit.prestaflow.domain.orders.model.Order
import kotlinx.coroutines.flow.Flow

interface OrdersRepository {
    fun observeOrders(): Flow<List<Order>>
    suspend fun refresh(forceRemote: Boolean = false)
    fun getOrder(orderId: Long): Flow<Order?>
    suspend fun refreshOrder(orderId: Long)
}
