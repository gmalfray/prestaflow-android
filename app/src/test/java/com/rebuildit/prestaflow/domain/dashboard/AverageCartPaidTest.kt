package com.rebuildit.prestaflow.domain.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartPoint
import com.rebuildit.prestaflow.domain.dashboard.model.averageCartPaid
import org.junit.Assert.assertEquals
import org.junit.Test

class AverageCartPaidTest {
    @Test
    fun `panier moyen calculé sur les seules commandes payantes`() {
        // 100 € sur 25 commandes dont 21 offertes : 4 payantes -> 25 € (et non 4 €)
        assertEquals(25.0, averageCartPaid(100.0, ordersCount = 25, ordersFreeCount = 21), 0.0001)
    }

    @Test
    fun `sans commande payante le panier moyen vaut 0`() {
        assertEquals(0.0, averageCartPaid(0.0, ordersCount = 3, ordersFreeCount = 3), 0.0)
        assertEquals(0.0, averageCartPaid(0.0, ordersCount = 0, ordersFreeCount = 0), 0.0)
    }

    @Test
    fun `sans commande offerte on retrouve CA divisé par commandes`() {
        assertEquals(50.0, averageCartPaid(200.0, ordersCount = 4, ordersFreeCount = 0), 0.0)
    }

    @Test
    fun `un point du graphe expose payantes et panier moyen payantes`() {
        val point = DashboardChartPoint("L", orders = 5, customers = 1, turnover = 90.0, ordersFree = 2)
        assertEquals(3, point.paidOrders)
        assertEquals(30.0, point.avgCartPaid, 0.0)
        assertEquals(0.0, DashboardChartPoint("M", orders = 2, customers = 1, turnover = 0.0, ordersFree = 2).avgCartPaid, 0.0)
    }
}
