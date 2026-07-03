package com.rebuildit.prestaflow.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adaptateur WorkManager : boucle sur la file offline et délègue le traitement de chaque
 * tâche à [SyncTaskExecutor] (qui route chaque requête vers la boutique **stockée sur la
 * tâche**, pas la boutique active courante — cf. sa documentation).
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val syncQueueRepository: SyncQueueRepository,
        private val taskExecutor: SyncTaskExecutor,
    ) : CoroutineWorker(appContext, workerParams) {
        @Suppress("InjectDispatcher") // WorkManager AssistedInject : dispatcher non injectable via WorkerParameters
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val tasks = syncQueueRepository.pendingTasks()
                if (tasks.isEmpty()) return@withContext Result.success()

                for (task in tasks) {
                    when (taskExecutor.execute(task)) {
                        is Result.Retry -> return@withContext Result.retry()
                        is Result.Failure -> return@withContext Result.failure()
                        is Result.Success -> { /* continue with next task */ }
                    }
                }
                Result.success()
            }
    }
