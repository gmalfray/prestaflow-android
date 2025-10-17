package com.rebuildit.prestaflow.domain.sync.model

data class PendingSyncTask(
    val id: Long,
    val endpoint: String,
    val method: String,
    val payloadJson: String,
    val resourceType: String?,
    val resourceId: Long?,
    val attemptCount: Int,
    val lastAttemptIso: String?,
    val createdAtIso: String,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.LAST_WRITE_WINS
)
