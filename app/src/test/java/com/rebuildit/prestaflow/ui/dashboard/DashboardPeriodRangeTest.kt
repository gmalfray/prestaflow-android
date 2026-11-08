package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DashboardPeriodRangeTest {
    private val today = LocalDate.of(2026, 11, 9) // lundi

    @Test
    fun `presets - du début de période à la fin de journée courante`() {
        assertEquals("2026-11-09 00:00:00" to "2026-11-09 23:59:59", periodDateRange(DashboardPeriod.TODAY, null, today))
        assertEquals("2026-11-09 00:00:00" to "2026-11-09 23:59:59", periodDateRange(DashboardPeriod.WEEK, null, today))
        assertEquals("2026-11-01 00:00:00" to "2026-11-09 23:59:59", periodDateRange(DashboardPeriod.MONTH, null, today))
        assertEquals("2026-10-01 00:00:00" to "2026-11-09 23:59:59", periodDateRange(DashboardPeriod.QUARTER, null, today))
        assertEquals("2026-01-01 00:00:00" to "2026-11-09 23:59:59", periodDateRange(DashboardPeriod.YEAR, null, today))
    }

    @Test
    fun `plage libre - ses dates, fin de journée incluse`() {
        assertEquals(
            "2026-03-02 00:00:00" to "2026-03-15 23:59:59",
            periodDateRange(DashboardPeriod.TODAY, "2026-03-02" to "2026-03-15", today),
        )
    }

    @Test
    fun `rows ignore les widgets exclus sans modifier la disposition`() {
        val layout =
            DashboardLayout(
                DashboardLayout.DEFAULT.items.map { it.copy(visible = it.widget in setOf(DashboardWidget.REVIEWS, DashboardWidget.CARTS)) },
            )
        assertEquals(listOf(listOf(DashboardWidget.CARTS, DashboardWidget.REVIEWS)), layout.rows())
        assertEquals(listOf(listOf(DashboardWidget.CARTS)), layout.rows(hidden = setOf(DashboardWidget.REVIEWS)))
    }
}
