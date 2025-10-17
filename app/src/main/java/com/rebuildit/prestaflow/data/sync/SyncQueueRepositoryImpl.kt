package com.rebuildit.prestaflow.data.sync

import com.rebuildit.prestaflow.data.local.dao.PendingSyncDao
import com.rebuildit.prestaflow.data.local.entity.PendingSyncEntity
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SyncQueueRepositoryImpl @Inject constructor(
    private val pendingSyncDao: PendingSyncDao
) : SyncQueueRepository {

    override fun observeQueue(): Flow<List<PendingSyncTask>> =
        pendingSyncDao.observeQueue().map { entities -> entities.map { it.toDomain() } }

    override suspend fun enqueue(
        endpoint: String,
        method: String,
        payloadJson: String,
        resourceType: String?,
        resourceId: Long?,
        conflictStrategy: ConflictStrategy
    ): Long {
        val entity = PendingSyncEntity(
            endpoint = endpoint,
            method = method,
            payloadJson = payloadJson,
            resourceType = resourceType,
            resourceId = resourceId,
            conflictStrategy = conflictStrategy.name,
            createdAtIso = java.time.Instant.now().toString()
        )
        return pendingSyncDao.enqueue(entity)
    }

    override suspend fun pendingTasks(): List<PendingSyncTask> =
        pendingSyncDao.getQueue().map { it.toDomain() }

    override suspend fun markAttempt(taskId: Long, attemptIso: String) {
        pendingSyncDao.incrementAttempt(taskId, attemptIso)
    }

    override suspend fun remove(taskId: Long) {
        pendingSyncDao.delete(taskId)
    }

    override suspend fun clear() {
        pendingSyncDao.clearAll()
    }

    private fun PendingSyncEntity.toDomain(): PendingSyncTask = PendingSyncTask(
        id = id,
        endpoint = endpoint,
        method = method,
        payloadJson = payloadJson,
        resourceType = resourceType,
        resourceId = resourceId,
        attemptCount = attemptCount,
        lastAttemptIso = lastAttemptIso,
        createdAtIso = createdAtIso,
        conflictStrategy = runCatching {
            ConflictStrategy.valueOf(conflictStrategy)
        }.getOrElse { ConflictStrategy.LAST_WRITE_WINS }
    )
}
