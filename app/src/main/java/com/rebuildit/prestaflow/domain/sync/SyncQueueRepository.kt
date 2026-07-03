package com.rebuildit.prestaflow.domain.sync

import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import kotlinx.coroutines.flow.Flow

interface SyncQueueRepository {
    fun observeQueue(): Flow<List<PendingSyncTask>>

    // Contrat d'enqueue : endpoint, méthode, payload + métadonnées optionnelles pour résolution de conflits.
    // shopUrl est OBLIGATOIRE (pas de défaut) : c'est la boutique active au moment de
    // l'enfilement, figée sur la tâche pour que SyncWorker la rejoue contre la bonne boutique
    // même si l'utilisateur bascule vers une autre boutique avant l'exécution.
    @Suppress("LongParameterList")
    suspend fun enqueue(
        endpoint: String,
        method: String,
        payloadJson: String,
        shopUrl: String,
        resourceType: String? = null,
        resourceId: Long? = null,
        conflictStrategy: ConflictStrategy = ConflictStrategy.LAST_WRITE_WINS,
    ): Long

    suspend fun pendingTasks(): List<PendingSyncTask>

    suspend fun markAttempt(
        taskId: Long,
        attemptIso: String,
    )

    suspend fun remove(taskId: Long)

    suspend fun clear()
}
