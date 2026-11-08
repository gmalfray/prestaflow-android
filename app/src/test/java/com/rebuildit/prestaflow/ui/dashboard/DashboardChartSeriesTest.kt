package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartMode
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardChartSeriesTest {
    private val points =
        listOf(
            DashboardChartPoint("L", orders = 5, customers = 1, turnover = 90.0, newCustomers = 2, ordersFree = 2, turnoverPrevious = 70.0),
            DashboardChartPoint("M", orders = 3, customers = 1, turnover = 0.0, newCustomers = 0, ordersFree = 3, turnoverPrevious = 40.0),
        )

    @Test
    fun `CA vs N-1 - deux séries sur le même axe`() {
        val s = buildChartSeries(DashboardChartMode.TURNOVER_VS_PREVIOUS, points)
        assertEquals(listOf(ChartSeriesKind.TURNOVER, ChartSeriesKind.TURNOVER_PREVIOUS), s.map { it.kind })
        assertEquals(listOf(ChartAxisSide.LEFT, ChartAxisSide.LEFT), s.map { it.side })
        assertEquals(listOf(70.0, 40.0), s[1].values)
    }

    @Test
    fun `CA plus panier moyen - panier sur l'axe droit, 0 sans payante`() {
        val s = buildChartSeries(DashboardChartMode.TURNOVER_AND_AVG_CART, points)
        assertEquals(ChartAxisSide.RIGHT, s[1].side)
        assertEquals(listOf(30.0, 0.0), s[1].values) // 90 / (5-2) ; 0 payante -> 0
    }

    @Test
    fun `payantes vs offertes`() {
        val s = buildChartSeries(DashboardChartMode.PAID_VS_FREE, points)
        assertEquals(listOf(3.0, 0.0), s[0].values)
        assertEquals(listOf(2.0, 3.0), s[1].values)
        assertEquals(2, s.size)
    }

    @Test
    fun `payantes vs offertes avec superposition nouveaux clients`() {
        val s = buildChartSeries(DashboardChartMode.PAID_VS_FREE, points, showNewCustomers = true)
        assertEquals(ChartSeriesKind.NEW_CUSTOMERS, s[2].kind)
        assertEquals(listOf(2.0, 0.0), s[2].values)
    }

    @Test
    fun `panier moyen seul`() {
        val s = buildChartSeries(DashboardChartMode.AVG_CART, points)
        assertEquals(1, s.size)
        assertEquals(listOf(30.0, 0.0), s[0].values)
    }
}
