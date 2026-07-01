package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en mémoire de [OrdersPreferencesRepository].
 * Expose des helpers pour piloter les flux depuis les tests.
 */
class FakeOrdersPreferencesRepository : OrdersPreferencesRepository {
    private val _visibleStatusIds = MutableStateFlow<Set<Int>?>(null)
    private val _swipeEnabled = MutableStateFlow(true)
    private val _swipeSourceStatusId = MutableStateFlow<Int?>(null)
    private val _swipeLeftTargetStatusId = MutableStateFlow<Int?>(null)
    private val _swipeRightTargetStatusId = MutableStateFlow<Int?>(null)

    // ── Filtre de statuts ──────────────────────────────────────────────────────

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

    // ── Swipe ─────────────────────────────────────────────────────────────────

    override val swipeEnabled: Flow<Boolean> = _swipeEnabled
    override val swipeSourceStatusId: Flow<Int?> = _swipeSourceStatusId
    override val swipeLeftTargetStatusId: Flow<Int?> = _swipeLeftTargetStatusId
    override val swipeRightTargetStatusId: Flow<Int?> = _swipeRightTargetStatusId

    override suspend fun setSwipeEnabled(enabled: Boolean) {
        _swipeEnabled.value = enabled
    }

    override suspend fun setSwipeSourceStatusId(id: Int?) {
        _swipeSourceStatusId.value = id
    }

    override suspend fun setSwipeLeftTargetStatusId(id: Int?) {
        _swipeLeftTargetStatusId.value = id
    }

    override suspend fun setSwipeRightTargetStatusId(id: Int?) {
        _swipeRightTargetStatusId.value = id
    }

    // ── Helpers de test ───────────────────────────────────────────────────────

    fun emitSwipeEnabled(enabled: Boolean) { _swipeEnabled.value = enabled }
    fun emitSwipeSourceStatusId(id: Int?) { _swipeSourceStatusId.value = id }
    fun emitSwipeLeftTargetStatusId(id: Int?) { _swipeLeftTargetStatusId.value = id }
    fun emitSwipeRightTargetStatusId(id: Int?) { _swipeRightTargetStatusId.value = id }
}
