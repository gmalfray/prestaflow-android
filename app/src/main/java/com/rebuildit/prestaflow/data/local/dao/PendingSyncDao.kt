package com.rebuildit.prestaflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rebuildit.prestaflow.data.local.entity.PendingSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDao {

    @Query("SELECT * FROM pending_sync ORDER BY created_at_iso ASC")
    fun observeQueue(): Flow<List<PendingSyncEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: PendingSyncEntity): Long

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_sync SET attempt_count = attempt_count + 1, last_attempt_iso = :lastAttemptIso WHERE id = :id")
    suspend fun incrementAttempt(id: Long, lastAttemptIso: String)

    @Query("DELETE FROM pending_sync")
    suspend fun clearAll()
}
