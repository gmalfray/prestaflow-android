package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.data.local.dao.OrderDao
import com.rebuildit.prestaflow.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en mémoire de [OrderDao].
 * Enregistre les appels à [clear] et [upsertOrders] pour vérification dans les tests.
 */
class FakeOrderDao : OrderDao {
    private val storeState = MutableStateFlow<List<OrderEntity>>(emptyList())

    var clearCallCount = 0
    val upsertedBatches = mutableListOf<List<OrderEntity>>()

    override fun observeOrders(): Flow<List<OrderEntity>> = storeState

    override fun observeOrder(orderId: Long): Flow<OrderEntity?> = MutableStateFlow(storeState.value.find { it.id == orderId })

    override suspend fun getPosition(orderId: Long): Int? = storeState.value.find { it.id == orderId }?.position

    override suspend fun upsertOrders(entities: List<OrderEntity>) {
        upsertedBatches += entities
        val current = storeState.value.toMutableList()
        entities.forEach { new ->
            val idx = current.indexOfFirst { it.id == new.id }
            if (idx >= 0) current[idx] = new else current += new
        }
        storeState.value = current
    }

    override suspend fun clear() {
        clearCallCount++
        storeState.value = emptyList()
    }

    fun currentEntities(): List<OrderEntity> = storeState.value
}
