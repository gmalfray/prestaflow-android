package com.rebuildit.prestaflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rebuildit.prestaflow.data.local.entity.DashboardMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {

    @Query("SELECT * FROM dashboard_metrics WHERE period = :period LIMIT 1")
    fun observeByPeriod(period: String): Flow<DashboardMetricEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DashboardMetricEntity)
}
