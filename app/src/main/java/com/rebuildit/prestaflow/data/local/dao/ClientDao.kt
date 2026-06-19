package com.rebuildit.prestaflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rebuildit.prestaflow.data.local.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY total_spent DESC LIMIT :limit")
    fun observeTopClients(limit: Int): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :clientId")
    fun observeClient(clientId: Long): Flow<ClientEntity?>

    @Query("SELECT * FROM clients WHERE id = :clientId")
    suspend fun getById(clientId: Long): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ClientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClientEntity)

    @Query("DELETE FROM clients")
    suspend fun clearAll()
}
