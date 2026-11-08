package com.rebuildit.prestaflow.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardOrdersSplitTest {
    @Test
    fun `part de payantes`() {
        assertEquals(0.8f, paidOrdersShare(ordersCount = 10, ordersFreeCount = 2), 0.0001f)
        assertEquals(1f, paidOrdersShare(ordersCount = 5, ordersFreeCount = 0), 0f)
        assertEquals(0f, paidOrdersShare(ordersCount = 5, ordersFreeCount = 5), 0f)
    }

    @Test
    fun `sans commande ou offertes supérieures au total - borné`() {
        assertEquals(0f, paidOrdersShare(ordersCount = 0, ordersFreeCount = 0), 0f)
        assertEquals(0f, paidOrdersShare(ordersCount = 3, ordersFreeCount = 9), 0f)
    }
}
