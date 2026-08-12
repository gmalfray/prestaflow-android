package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import com.rebuildit.prestaflow.domain.orders.model.OrdersSeenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en mémoire de [OrdersPreferencesRepository].
 * Expose des helpers pour piloter les flux depuis les tests.
 */
class FakeOrdersPreferencesRepository : OrdersPreferencesRepository {
    private val _visibleStatusIds = MutableStateFlow<Set<Int>?>(null)

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

    // ── Pastille "commandes non vues" ────────────────────────────────────────

    private val seenStateByShop = mutableMapOf<String, MutableStateFlow<OrdersSeenState>>()

    /** Appels reçus par [markOrdersListSeen] : (shopId, maxOrderId). */
    val markOrdersListSeenCalls = mutableListOf<Pair<String, Long>>()

    /** Appels reçus par [markOrderSeen] : (shopId, orderId). */
    val markOrderSeenCalls = mutableListOf<Pair<String, Long>>()

    private fun stateFlowFor(shopId: String) = seenStateByShop.getOrPut(shopId) { MutableStateFlow(OrdersSeenState()) }

    /** Prépose un état "vu" pour [shopId], sans passer par [markOrdersListSeen]/[markOrderSeen]. */
    fun seedSeenState(
        shopId: String,
        state: OrdersSeenState,
    ) {
        stateFlowFor(shopId).value = state
    }

    override fun ordersSeenState(shopId: String): Flow<OrdersSeenState> = stateFlowFor(shopId)

    override suspend fun markOrdersListSeen(
        shopId: String,
        maxOrderId: Long,
    ) {
        markOrdersListSeenCalls += shopId to maxOrderId
        val flow = stateFlowFor(shopId)
        val newLastSeen = maxOf(flow.value.lastSeenOrderId, maxOrderId)
        flow.value =
            flow.value.copy(
                lastSeenOrderId = newLastSeen,
                individuallySeenIds = flow.value.individuallySeenIds.filter { it > newLastSeen }.toSet(),
            )
    }

    override suspend fun markOrderSeen(
        shopId: String,
        orderId: Long,
    ) {
        markOrderSeenCalls += shopId to orderId
        val flow = stateFlowFor(shopId)
        if (orderId <= flow.value.lastSeenOrderId) return
        flow.value = flow.value.copy(individuallySeenIds = flow.value.individuallySeenIds + orderId)
    }
}
