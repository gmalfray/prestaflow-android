package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.products.ReplenishSessionRepository
import com.rebuildit.prestaflow.domain.products.model.ReplenishLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake en mémoire de [ReplenishSessionRepository], synchrone (pas de Room) — pour les tests ViewModel. */
class FakeReplenishSessionRepository : ReplenishSessionRepository {
    private val entriesFlow = MutableStateFlow<List<ReplenishLogEntry>>(emptyList())
    private var nextId = 1L

    override fun observeEntries(): Flow<List<ReplenishLogEntry>> = entriesFlow.asStateFlow()

    override suspend fun getEntries(): List<ReplenishLogEntry> = entriesFlow.value

    override suspend fun addOrMerge(
        productId: Long,
        combinationId: Long?,
        warehouseId: Long?,
        productName: String,
        delta: Int,
    ) {
        val current = entriesFlow.value
        val existing =
            current.firstOrNull {
                it.productId == productId && it.combinationId == combinationId && it.warehouseId == warehouseId
            }
        entriesFlow.value =
            if (existing != null) {
                current.map { if (it.id == existing.id) it.copy(delta = it.delta + delta) else it }
            } else {
                current +
                    ReplenishLogEntry(
                        id = nextId++,
                        productId = productId,
                        combinationId = combinationId,
                        warehouseId = warehouseId,
                        productName = productName,
                        delta = delta,
                    )
            }
    }

    override suspend fun removeEntry(id: Long) {
        entriesFlow.value = entriesFlow.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        entriesFlow.value = emptyList()
    }
}
