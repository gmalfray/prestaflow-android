package com.rebuildit.prestaflow.core.sync

import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SyncConflictResolver @Inject constructor() {

    fun resolve(
        task: PendingSyncTask,
        responseCode: Int,
        responseBody: String?
    ): ConflictResolution {
        Timber.w("Conflict detected for ${task.resourceType}:${task.resourceId} (code=$responseCode)")
        return when (task.conflictStrategy) {
            ConflictStrategy.LAST_WRITE_WINS -> ConflictResolution.Retry
            ConflictStrategy.MERGE -> ConflictResolution.Hold("Merge required before retry")
            ConflictStrategy.PROMPT_USER -> ConflictResolution.Hold("Awaiting user decision")
        }
    }
}
