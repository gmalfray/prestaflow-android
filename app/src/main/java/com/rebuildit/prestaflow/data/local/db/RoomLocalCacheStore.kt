package com.rebuildit.prestaflow.data.local.db

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLocalCacheStore
    @Inject
    constructor(
        private val database: PrestaFlowDatabase,
    ) : LocalCacheStore {
        override suspend fun clearAll() {
            database.clearAllTables()
        }
    }
