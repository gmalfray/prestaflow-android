package com.rebuildit.prestaflow.core.sync

import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncConflictResolver
    @Inject
    constructor() {
        @Suppress("UnusedParameter") // responseBody réservé pour futures stratégies MERGE qui en ont besoin
        fun resolve(
            task: PendingSyncTask,
            responseCode: Int,
            responseBody: String?,
        ): ConflictResolution {
            Timber.w("Conflict detected for ${task.resourceType}:${task.resourceId} (code=$responseCode)")
            return when (task.conflictStrategy) {
                ConflictStrategy.LAST_WRITE_WINS -> ConflictResolution.Retry
                ConflictStrategy.MERGE -> ConflictResolution.Hold("Merge required before retry")
                ConflictStrategy.PROMPT_USER -> ConflictResolution.Hold("Awaiting user decision")
            }
        }
    }
