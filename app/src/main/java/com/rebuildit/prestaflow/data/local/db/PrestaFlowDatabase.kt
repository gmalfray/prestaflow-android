package com.rebuildit.prestaflow.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rebuildit.prestaflow.data.local.dao.ClientDao
import com.rebuildit.prestaflow.data.local.dao.DashboardDao
import com.rebuildit.prestaflow.data.local.dao.OrderDao
import com.rebuildit.prestaflow.data.local.dao.PendingSyncDao
import com.rebuildit.prestaflow.data.local.dao.ProductDao
import com.rebuildit.prestaflow.data.local.dao.StockAvailabilityDao
import com.rebuildit.prestaflow.data.local.entity.ClientEntity
import com.rebuildit.prestaflow.data.local.entity.DashboardMetricEntity
import com.rebuildit.prestaflow.data.local.entity.OrderEntity
import com.rebuildit.prestaflow.data.local.entity.PendingSyncEntity
import com.rebuildit.prestaflow.data.local.entity.ProductEntity
import com.rebuildit.prestaflow.data.local.entity.StockAvailabilityEntity

@Database(
    entities = [
        OrderEntity::class,
        ProductEntity::class,
        DashboardMetricEntity::class,
        PendingSyncEntity::class,
        StockAvailabilityEntity::class,
        ClientEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class PrestaFlowDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun stockAvailabilityDao(): StockAvailabilityDao
    abstract fun clientDao(): ClientDao
}
