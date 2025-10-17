package com.rebuildit.prestaflow.domain.sync

import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import kotlinx.coroutines.flow.Flow

interface SyncQueueRepository {
    fun observeQueue(): Flow<List<PendingSyncTask>>
    suspend fun enqueue(
        endpoint: String,
        method: String,
        payloadJson: String,
        resourceType: String? = null,
        resourceId: Long? = null,
        conflictStrategy: ConflictStrategy = ConflictStrategy.LAST_WRITE_WINS
    ): Long

    suspend fun pendingTasks(): List<PendingSyncTask>
    suspend fun markAttempt(taskId: Long, attemptIso: String)
    suspend fun remove(taskId: Long)
    suspend fun clear()
}
