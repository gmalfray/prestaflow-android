package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.core.notifications.SyncFailureNotifierContract
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask

/**
 * Fake de [SyncFailureNotifierContract] pour les tests unitaires : enregistre les appels sans
 * afficher de notification système (pas de Context en JVM pur).
 */
class FakeSyncFailureNotifier : SyncFailureNotifierContract {
    data class DroppedCall(val task: PendingSyncTask, val httpCode: Int)

    val droppedCalls: MutableList<DroppedCall> = mutableListOf()

    override fun notifyTaskDropped(
        task: PendingSyncTask,
        httpCode: Int,
    ) {
        droppedCalls += DroppedCall(task, httpCode)
    }
}
