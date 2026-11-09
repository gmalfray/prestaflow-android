package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayoutItem
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardLayoutReorderTest {
    private val a = DashboardWidget.entries[0]
    private val b = DashboardWidget.entries[1]
    private val c = DashboardWidget.entries[2]
    private val d = DashboardWidget.entries[3]

    @Test
    fun `les widgets masques gardent leur emplacement`() {
        val layout =
            DashboardLayout(
                listOf(
                    DashboardLayoutItem(a, true),
                    DashboardLayoutItem(b, false),
                    DashboardLayoutItem(c, true),
                    DashboardLayoutItem(d, true),
                ),
            )
        val result = layout.withVisibleOrder(listOf(d, a, c))
        assertEquals(listOf(d, b, a, c), result.items.map { it.widget })
        assertEquals(listOf(true, false, true, true), result.items.map { it.visible })
        assertEquals(listOf(d, a, c), result.visibleWidgets)
    }
}
