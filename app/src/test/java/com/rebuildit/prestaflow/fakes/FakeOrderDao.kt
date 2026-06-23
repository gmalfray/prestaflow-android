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

    private val _store = MutableStateFlow<List<OrderEntity>>(emptyList())

    var clearCallCount = 0
    val upsertedBatches = mutableListOf<List<OrderEntity>>()

    override fun observeOrders(): Flow<List<OrderEntity>> = _store

    override fun observeOrder(orderId: Long): Flow<OrderEntity?> =
        MutableStateFlow(_store.value.find { it.id == orderId })

    override suspend fun upsertOrders(entities: List<OrderEntity>) {
        upsertedBatches += entities
        val current = _store.value.toMutableList()
        entities.forEach { new ->
            val idx = current.indexOfFirst { it.id == new.id }
            if (idx >= 0) current[idx] = new else current += new
        }
        _store.value = current
    }

    override suspend fun clear() {
        clearCallCount++
        _store.value = emptyList()
    }

    fun currentEntities(): List<OrderEntity> = _store.value
}
