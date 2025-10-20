package com.rebuildit.prestaflow.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.sync.ConflictResolution.Drop
import com.rebuildit.prestaflow.core.sync.ConflictResolution.Hold
import com.rebuildit.prestaflow.core.sync.ConflictResolution.Retry
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val endpointManager: ApiEndpointManager,
    private val okHttpClient: OkHttpClient,
    private val syncQueueRepository: SyncQueueRepository,
    private val conflictResolver: SyncConflictResolver
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tasks = syncQueueRepository.pendingTasks()
        if (tasks.isEmpty()) return@withContext Result.success()

        for (task in tasks) {
            when (processTask(task)) {
                is Result.Retry -> return@withContext Result.retry()
                is Result.Failure -> return@withContext Result.failure()
                is Result.Success -> { /* continue with next task */ }
            }
        }
        Result.success()
    }

    private suspend fun processTask(task: PendingSyncTask): Result {
        val request = buildRequest(task)
        val now = Instant.now().toString()
        val response = runCatching { okHttpClient.newCall(request).execute() }
        syncQueueRepository.markAttempt(task.id, now)
        return response.fold(
            onSuccess = { httpResponse ->
                httpResponse.use { resp ->
                    if (resp.isSuccessful) {
                        syncQueueRepository.remove(task.id)
                        Result.success()
                    } else if (resp.code == 409) {
                        val body = resp.body?.string()
                        when (val resolution = conflictResolver.resolve(task, resp.code, body)) {
                            Drop -> {
                                syncQueueRepository.remove(task.id)
                                Result.success()
                            }
                            Retry -> Result.retry()
                            is Hold -> {
                                Timber.w("Holding task ${task.id}: ${resolution.reason}")
                                Result.success()
                            }
                        }
                    } else if (resp.code in 500..599) {
                        Timber.w("Server error ${resp.code} for ${task.endpoint}")
                        Result.retry()
                    } else {
                        Timber.w("Dropping task ${task.id} after response ${resp.code}")
                        syncQueueRepository.remove(task.id)
                        Result.success()
                    }
                }
            },
            onFailure = { error ->
                Timber.w(error, "Failed to execute sync task ${task.id}")
                Result.retry()
            }
        )
    }

    private fun buildRequest(task: PendingSyncTask): Request {
        val method = task.method.uppercase()
        val baseUrl = endpointManager.getActiveBaseUrl().toString().trimEnd('/')
        val url = baseUrl + "/" + task.endpoint.trimStart('/')
        val body = when (method) {
            "POST", "PUT", "PATCH" -> task.payloadJson.takeIf { it.isNotBlank() }?.toJsonRequestBody()
                ?: "{}".toJsonRequestBody()
            else -> null
        }
        return Request.Builder()
            .url(url)
            .method(method, body)
            .build()
    }

    private fun String.toJsonRequestBody(): RequestBody =
        this.toRequestBody("application/json".toMediaType())
}
