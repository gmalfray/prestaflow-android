package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake en mémoire de [SyncQueueRepository] pour les tests JVM (SyncTaskExecutor, ProductsRepositoryImpl,
 * OrdersRepositoryImpl).
 */
class FakeSyncQueueRepository : SyncQueueRepository {
    private val _tasks = MutableStateFlow<List<PendingSyncTask>>(emptyList())

    /** Appels reçus par [enqueue] : (endpoint, method, payloadJson, shopUrl, resourceType, resourceId, conflictStrategy). */
    data class EnqueueCall(
        val endpoint: String,
        val method: String,
        val payloadJson: String,
        val shopUrl: String,
        val resourceType: String?,
        val resourceId: Long?,
        val conflictStrategy: ConflictStrategy,
    )

    val enqueueCalls = mutableListOf<EnqueueCall>()
    val removedIds = mutableListOf<Long>()
    val markAttemptCalls = mutableListOf<Long>()

    private var nextId = 1L

    override fun observeQueue(): Flow<List<PendingSyncTask>> = _tasks.asStateFlow()

    override suspend fun enqueue(
        endpoint: String,
        method: String,
        payloadJson: String,
        shopUrl: String,
        resourceType: String?,
        resourceId: Long?,
        conflictStrategy: ConflictStrategy,
    ): Long {
        val id = nextId++
        enqueueCalls += EnqueueCall(endpoint, method, payloadJson, shopUrl, resourceType, resourceId, conflictStrategy)
        _tasks.value +=
            PendingSyncTask(
                id = id,
                endpoint = endpoint,
                method = method,
                payloadJson = payloadJson,
                resourceType = resourceType,
                resourceId = resourceId,
                attemptCount = 0,
                lastAttemptIso = null,
                createdAtIso = "2026-01-01T00:00:00Z",
                conflictStrategy = conflictStrategy,
                shopUrl = shopUrl,
            )
        return id
    }

    override suspend fun pendingTasks(): List<PendingSyncTask> = _tasks.value

    override suspend fun markAttempt(
        taskId: Long,
        attemptIso: String,
    ) {
        markAttemptCalls += taskId
    }

    override suspend fun remove(taskId: Long) {
        removedIds += taskId
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
    }

    override suspend fun clear() {
        _tasks.value = emptyList()
    }
}
