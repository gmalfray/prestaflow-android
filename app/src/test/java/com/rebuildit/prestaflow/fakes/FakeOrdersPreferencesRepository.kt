package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en mémoire de [OrdersPreferencesRepository].
 * Expose [emitVisibleStatusIds] pour piloter le flux depuis les tests.
 */
class FakeOrdersPreferencesRepository : OrdersPreferencesRepository {

    private val _visibleStatusIds = MutableStateFlow<Set<Int>?>(null)

    /** Émet une nouvelle valeur dans le flux des IDs visibles. */
    fun emitVisibleStatusIds(ids: Set<Int>?) {
        _visibleStatusIds.value = ids
    }

    override val visibleStatusIds: Flow<Set<Int>?> = _visibleStatusIds

    /** Dernière valeur persistée par [setVisibleStatusIds]. */
    var storedIds: Set<Int>? = null

    var clearCalled = false

    override suspend fun setVisibleStatusIds(ids: Set<Int>) {
        storedIds = ids
        _visibleStatusIds.value = ids
    }

    override suspend fun clearVisibleStatusIds() {
        clearCalled = true
        storedIds = null
        _visibleStatusIds.value = null
    }
}
