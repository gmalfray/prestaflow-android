package com.rebuildit.prestaflow.core.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.rebuildit.prestaflow.core.connectivity.ConnectivityMonitor
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class SyncOrchestrator @Inject constructor(
    private val queueRepository: SyncQueueRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val workManager: WorkManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                queueRepository.observeQueue(),
                connectivityMonitor.isConnected
            ) { queue, connected -> queue.isNotEmpty() && connected }
                .distinctUntilChanged()
                .collectLatest { shouldSync ->
                    if (shouldSync) {
                        scheduleSync()
                    }
                }
        }
    }

    fun scheduleSync() {
        Timber.d("Scheduling sync work")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "prestaflow_sync_queue"
    }
}
