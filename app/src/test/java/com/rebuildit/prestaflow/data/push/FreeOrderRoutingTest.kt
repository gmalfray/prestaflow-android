package com.rebuildit.prestaflow.data.push

import com.rebuildit.prestaflow.core.notifications.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeOrderRoutingTest {
    private val free = mapOf("is_free" to "1", "order_total" to "0")
    private val paid = mapOf("is_free" to "0", "order_total" to "29.9")

    @Test
    fun `id du canal commandes offertes aligne sur le hub`() {
        assertEquals("sales_free_v1", NotificationChannels.CHANNEL_SALES_FREE)
    }

    @Test
    fun `commande offerte detectee`() {
        assertTrue(isFreeOrderPush("order.created", free))
    }

    @Test
    fun `commande payante ou push sans is_free non offerts`() {
        assertFalse(isFreeOrderPush("order.created", paid))
        assertFalse(isFreeOrderPush("order.created", emptyMap()))
    }

    @Test
    fun `autres evenements jamais offerts meme avec is_free`() {
        assertFalse(isFreeOrderPush("order.status.changed", free))
        assertFalse(isFreeOrderPush(null, free))
    }

    @Test
    fun `order created offerte part sur le canal dedie`() {
        assertEquals(
            NotificationChannels.CHANNEL_SALES_FREE,
            NotificationChannels.channelForEvent("order.created", isFreeOrder = isFreeOrderPush("order.created", free)),
        )
    }

    @Test
    fun `order created payante reste sur le canal ventes`() {
        assertEquals(NotificationChannels.CHANNEL_SALES, NotificationChannels.channelForEvent("order.created", isFreeOrder = false))
    }

    @Test
    fun `is_free ignore pour les autres evenements`() {
        assertEquals(
            NotificationChannels.CHANNEL_ORDER_STATUS,
            NotificationChannels.channelForEvent("order.status.changed", isFreeOrder = true),
        )
    }
}
