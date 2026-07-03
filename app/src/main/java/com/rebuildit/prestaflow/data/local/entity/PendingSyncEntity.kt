package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endpoint: String,
    val method: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "resource_type") val resourceType: String? = null,
    @ColumnInfo(name = "resource_id") val resourceId: Long? = null,
    @ColumnInfo(name = "conflict_strategy") val conflictStrategy: String = "LAST_WRITE_WINS",
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_attempt_iso") val lastAttemptIso: String? = null,
    @ColumnInfo(name = "created_at_iso") val createdAtIso: String,
    // Boutique cible de la tâche, figée au moment de l'enfilement (cf. Migration 14->15).
    // Permet à SyncWorker de rejouer la tâche contre CETTE boutique, pas la boutique active
    // au moment de l'exécution (qui peut avoir changé entre-temps).
    @ColumnInfo(name = "shop_url", defaultValue = "''") val shopUrl: String = "",
)
