package com.rebuildit.prestaflow.data.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeOrderPushFilterTest {
    private val free = mapOf("is_free" to "1", "order_total" to "0", "id_customer" to "7")
    private val paid = mapOf("is_free" to "0", "order_total" to "29.9")

    @Test
    fun `commande offerte masquée quand la préférence est désactivée`() {
        assertFalse(shouldDisplayPush("order.created", free, notifyFreeOrders = false))
    }

    @Test
    fun `commande offerte affichée quand la préférence est active`() {
        assertTrue(shouldDisplayPush("order.created", free, notifyFreeOrders = true))
    }

    @Test
    fun `commande payante toujours affichée`() {
        assertTrue(shouldDisplayPush("order.created", paid, notifyFreeOrders = false))
    }

    @Test
    fun `push sans is_free (connecteur ancien) toujours affiché`() {
        assertTrue(shouldDisplayPush("order.created", emptyMap(), notifyFreeOrders = false))
    }

    @Test
    fun `autres événements non concernés même avec is_free`() {
        assertTrue(shouldDisplayPush("order.status_changed", free, notifyFreeOrders = false))
        assertFalse(isFreeOrderPush(null, free))
    }
}
