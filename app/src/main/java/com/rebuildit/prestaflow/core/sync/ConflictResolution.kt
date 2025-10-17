package com.rebuildit.prestaflow.core.sync

sealed class ConflictResolution {
    object Retry : ConflictResolution()
    object Drop : ConflictResolution()
    data class Hold(val reason: String? = null) : ConflictResolution()
}
