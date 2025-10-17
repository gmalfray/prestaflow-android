package com.rebuildit.prestaflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rebuildit.prestaflow.data.local.entity.StockAvailabilityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAvailabilityDao {

    @Query("SELECT * FROM stock_availabilities WHERE product_id = :productId ORDER BY warehouse_id ASC")
    fun observeForProduct(productId: Long): Flow<List<StockAvailabilityEntity>>

    @Query("SELECT * FROM stock_availabilities WHERE product_id = :productId")
    suspend fun getForProduct(productId: Long): List<StockAvailabilityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StockAvailabilityEntity>)

    @Query("DELETE FROM stock_availabilities WHERE product_id = :productId")
    suspend fun clearForProduct(productId: Long)
}
