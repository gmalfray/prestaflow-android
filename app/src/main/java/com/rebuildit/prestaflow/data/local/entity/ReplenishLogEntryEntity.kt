package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ligne persistée du journal de session de réappro (cf.
 * [com.rebuildit.prestaflow.domain.products.ReplenishSessionRepository]) — table dédiée plutôt que
 * réutiliser `pending_sync` : ce journal n'est PAS une file d'écritures déjà décidées à rejouer, mais
 * une liste éditable (fusionnable, annulable) tant que la session n'a pas été validée.
 */
@Entity(tableName = "replenish_log")
data class ReplenishLogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "combination_id") val combinationId: Long? = null,
    @ColumnInfo(name = "warehouse_id") val warehouseId: Long? = null,
    @ColumnInfo(name = "product_name") val productName: String,
    val delta: Int,
    @ColumnInfo(name = "created_at_iso") val createdAtIso: String,
)
