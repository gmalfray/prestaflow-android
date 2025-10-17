package com.rebuildit.prestaflow.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rebuildit.prestaflow.data.local.dao.DashboardDao
import com.rebuildit.prestaflow.data.local.dao.OrderDao
import com.rebuildit.prestaflow.data.local.dao.PendingSyncDao
import com.rebuildit.prestaflow.data.local.dao.ProductDao
import com.rebuildit.prestaflow.data.local.entity.DashboardMetricEntity
import com.rebuildit.prestaflow.data.local.entity.OrderEntity
import com.rebuildit.prestaflow.data.local.entity.PendingSyncEntity
import com.rebuildit.prestaflow.data.local.entity.ProductEntity

@Database(
    entities = [
        OrderEntity::class,
        ProductEntity::class,
        DashboardMetricEntity::class,
        PendingSyncEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PrestaFlowDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun pendingSyncDao(): PendingSyncDao
}
