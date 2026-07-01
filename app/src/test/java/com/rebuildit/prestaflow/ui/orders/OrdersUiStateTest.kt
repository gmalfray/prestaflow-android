package com.rebuildit.prestaflow.ui.orders

import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la logique pure de [OrdersUiState] (propriétés calculées).
 * Aucune dépendance Android ni coroutine : exécution JVM directe.
 */
class OrdersUiStateTest {
    // ─── visibleOrders ───────────────────────────────────────────────────────

    @Test
    fun `visibleOrders retourne toutes les commandes quand query est vide`() {
        val state = buildState(orders = listOf(buildOrder(1L, "REF001", "Alice Martin")))

        assertEquals(1, state.visibleOrders.size)
    }

    @Test
    fun `visibleOrders filtre sur le nom du client insensible a la casse`() {
        val state =
            buildState(
                orders =
                    listOf(
                        buildOrder(1L, "REF001", customerName = "Alice Martin"),
                        buildOrder(2L, "REF002", customerName = "Bob Dupont"),
                    ),
                query = "alice",
            )

        assertEquals(1, state.visibleOrders.size)
        assertEquals("REF001", state.visibleOrders.first().reference)
    }

    @Test
    fun `visibleOrders filtre sur la reference insensible a la casse`() {
        val state =
            buildState(
                orders =
                    listOf(
                        buildOrder(1L, "REF001", customerName = "Alice Martin"),
                        buildOrder(2L, "REF002", customerName = "Bob Dupont"),
                    ),
                query = "ref002",
            )

        assertEquals(1, state.visibleOrders.size)
        assertEquals("REF002", state.visibleOrders.first().reference)
    }

    @Test
    fun `visibleOrders retourne liste vide si aucune commande ne correspond`() {
        val state =
            buildState(
                orders = listOf(buildOrder(1L, "REF001", "Alice Martin")),
                query = "zzz",
            )

        assertTrue(state.visibleOrders.isEmpty())
    }

    @Test
    fun `visibleOrders retourne toutes les commandes si query est seulement des espaces`() {
        val state =
            buildState(
                orders = listOf(buildOrder(1L, "REF001", "Alice Martin")),
                query = "   ",
            )

        assertEquals(1, state.visibleOrders.size)
    }

    // ─── filteredStatuses ────────────────────────────────────────────────────

    @Test
    fun `filteredStatuses retourne tous les statuts si visibleStatusIds est null`() {
        val statuses =
            listOf(
                OrderStatusFilter(1, "Paiement accepté", "#00FF00"),
                OrderStatusFilter(2, "En préparation", "#0000FF"),
                OrderStatusFilter(3, "Expédié", "#FFA500"),
            )
        val state = buildState(availableStatuses = statuses, visibleStatusIds = null)

        assertEquals(3, state.filteredStatuses.size)
    }

    @Test
    fun `filteredStatuses ne retourne que les statuts dont l id est dans visibleStatusIds`() {
        val statuses =
            listOf(
                OrderStatusFilter(1, "Paiement accepté", "#00FF00"),
                OrderStatusFilter(2, "En préparation", "#0000FF"),
                OrderStatusFilter(3, "Expédié", "#FFA500"),
            )
        val state = buildState(availableStatuses = statuses, visibleStatusIds = setOf(1, 3))

        val filtered = state.filteredStatuses
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1 })
        assertTrue(filtered.any { it.id == 3 })
    }

    @Test
    fun `filteredStatuses retourne liste vide si visibleStatusIds ne correspond a aucun statut disponible`() {
        val statuses = listOf(OrderStatusFilter(1, "Paiement accepté", "#00FF00"))
        val state = buildState(availableStatuses = statuses, visibleStatusIds = setOf(99))

        assertTrue(state.filteredStatuses.isEmpty())
    }

    // ─── Builders ────────────────────────────────────────────────────────────

    private fun buildState(
        orders: List<Order> = emptyList(),
        query: String = "",
        availableStatuses: List<OrderStatusFilter> = emptyList(),
        visibleStatusIds: Set<Int>? = null,
        selectedStatusIds: Set<Int> = emptySet(),
    ) = OrdersUiState(
        orders = orders,
        query = query,
        availableStatuses = availableStatuses,
        visibleStatusIds = visibleStatusIds,
        selectedStatusIds = selectedStatusIds,
    )

    private fun buildOrder(
        id: Long,
        reference: String,
        customerName: String = "Client Test",
        status: String = "En préparation",
        hasInvoice: Boolean = false,
    ) = Order(
        id = id,
        reference = reference,
        status = status,
        totalPaid = 49.99,
        currency = "EUR",
        customerName = customerName,
        createdAtIso = "2024-01-01T00:00:00+00:00",
        updatedAtIso = "2024-01-02T00:00:00+00:00",
        hasInvoice = hasInvoice,
    )
}
