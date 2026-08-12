package com.rebuildit.prestaflow.data.products

import com.rebuildit.prestaflow.data.local.dao.ReplenishLogDao
import com.rebuildit.prestaflow.data.local.entity.ReplenishLogEntryEntity
import com.rebuildit.prestaflow.domain.products.ReplenishSessionRepository
import com.rebuildit.prestaflow.domain.products.model.ReplenishLogEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

class ReplenishSessionRepositoryImpl
    @Inject
    constructor(
        private val dao: ReplenishLogDao,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ReplenishSessionRepository {
        override fun observeEntries(): Flow<List<ReplenishLogEntry>> = dao.observeAll().map { entries -> entries.map { it.toDomain() } }

        override suspend fun getEntries(): List<ReplenishLogEntry> = withContext(ioDispatcher) { dao.getAll().map { it.toDomain() } }

        override suspend fun addOrMerge(
            productId: Long,
            combinationId: Long?,
            warehouseId: Long?,
            productName: String,
            delta: Int,
        ) {
            withContext(ioDispatcher) {
                // Recherche directe en base (pas via un état en mémoire potentiellement périmé) :
                // deux `addOrMerge` rapprochés doivent tous deux voir la ligne créée par le premier.
                val existing =
                    dao.getAll().firstOrNull {
                        it.productId == productId && it.combinationId == combinationId && it.warehouseId == warehouseId
                    }
                if (existing != null) {
                    dao.upsert(existing.copy(delta = existing.delta + delta))
                } else {
                    dao.upsert(
                        ReplenishLogEntryEntity(
                            productId = productId,
                            combinationId = combinationId,
                            warehouseId = warehouseId,
                            productName = productName,
                            delta = delta,
                            createdAtIso = Instant.now().toString(),
                        ),
                    )
                }
            }
        }

        override suspend fun removeEntry(id: Long) {
            withContext(ioDispatcher) { dao.delete(id) }
        }

        override suspend fun clear() {
            withContext(ioDispatcher) { dao.clearAll() }
        }
    }

private fun ReplenishLogEntryEntity.toDomain() =
    ReplenishLogEntry(
        id = id,
        productId = productId,
        combinationId = combinationId,
        warehouseId = warehouseId,
        productName = productName,
        delta = delta,
    )
