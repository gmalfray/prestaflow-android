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
    fun `filteredStatuses retourne le defaut curatie par ID (3 4 9) si visibleStatusIds est null`() {
        // Cas nominal : les 3 statuts cibles (IDs PrestaShop 3/4/9) sont présents
        val statuses =
            listOf(
                OrderStatusFilter(2, "Paiement accepté", "#00FF00"),
                OrderStatusFilter(3, "En préparation", "#0000FF"),
                OrderStatusFilter(4, "Expédié", "#FFA500"),
                OrderStatusFilter(9, "Terminée", "#008000"),
            )
        val state = buildState(availableStatuses = statuses, visibleStatusIds = null)

        val filtered = state.filteredStatuses
        assertEquals(3, filtered.size)
        assertTrue(filtered.any { it.id == 3 }) // En préparation
        assertTrue(filtered.any { it.id == 4 }) // Expédié
        assertTrue(filtered.any { it.id == 9 }) // Terminée
    }

    @Test
    fun `filteredStatuses repli 3 premiers statuts si aucun nom ne matche et visibleStatusIds est null`() {
        val statuses =
            listOf(
                OrderStatusFilter(10, "Inconnu A", "#000000"),
                OrderStatusFilter(11, "Inconnu B", "#111111"),
                OrderStatusFilter(12, "Inconnu C", "#222222"),
                OrderStatusFilter(13, "Inconnu D", "#333333"),
            )
        val state = buildState(availableStatuses = statuses, visibleStatusIds = null)

        val filtered = state.filteredStatuses
        assertEquals(3, filtered.size)
        assertEquals(10, filtered[0].id)
        assertEquals(11, filtered[1].id)
        assertEquals(12, filtered[2].id)
    }

    @Test
    fun `filteredStatuses par defaut prend les IDs 3 4 9 dans l ordre et plafonne a MAX`() {
        val statuses =
            listOf(
                OrderStatusFilter(2, "Paiement accepté", "#00FF00"),
                OrderStatusFilter(3, "En cours de préparation", "#0000FF"),
                OrderStatusFilter(4, "Expédié", "#FFA500"),
                OrderStatusFilter(9, "Terminée", "#008000"),
                OrderStatusFilter(6, "Annulé", "#FF0000"),
            )
        val state = buildState(availableStatuses = statuses, visibleStatusIds = null)

        // Défaut par ID = [3, 4, 9] dans cet ordre, au plus MAX_VISIBLE_STATUS_CHIPS.
        val filtered = state.filteredStatuses
        assertEquals(MAX_VISIBLE_STATUS_CHIPS, filtered.size)
        assertEquals(listOf(3, 4, 9), filtered.map { it.id })
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
