package com.rebuildit.prestaflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rebuildit.prestaflow.data.local.entity.ReplenishLogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplenishLogDao {
    @Query("SELECT * FROM replenish_log ORDER BY id ASC")
    fun observeAll(): Flow<List<ReplenishLogEntryEntity>>

    @Query("SELECT * FROM replenish_log ORDER BY id ASC")
    suspend fun getAll(): List<ReplenishLogEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReplenishLogEntryEntity): Long

    @Query("DELETE FROM replenish_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM replenish_log")
    suspend fun clearAll()
}
