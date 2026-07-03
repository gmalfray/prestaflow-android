package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.data.local.db.LocalCacheStore

/** Fake de [LocalCacheStore] pour les tests unitaires : compte les purges sans toucher Room. */
class FakeLocalCacheStore : LocalCacheStore {
    var clearAllCallCount: Int = 0
        private set

    override suspend fun clearAll() {
        clearAllCallCount++
    }
}
