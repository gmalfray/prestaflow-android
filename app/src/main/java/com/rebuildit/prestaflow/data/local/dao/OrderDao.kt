package com.rebuildit.prestaflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rebuildit.prestaflow.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    // ORDER BY position : préserve l'ordre renvoyé par le serveur (tri date/montant/statut/réf).
    @Query("SELECT * FROM orders ORDER BY position ASC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :orderId")
    fun observeOrder(orderId: Long): Flow<OrderEntity?>

    /** Position actuelle d'une commande (pour la préserver lors d'un rafraîchissement du détail). */
    @Query("SELECT position FROM orders WHERE id = :orderId")
    suspend fun getPosition(orderId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(entities: List<OrderEntity>)

    @Query("DELETE FROM orders")
    suspend fun clear()
}
