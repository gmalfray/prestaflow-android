package com.rebuildit.prestaflow.core.notifications

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests unitaires de [NotificationChannels] : routage `event` → canal (7 catégories, dont les deux
 * nouvelles `sav.message`/`review.pending`, cf. CHANGELOG) et paramètres des canaux créés.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationChannelsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    // ─── Routage event → canal ─────────────────────────────────────────────────

    @Test
    fun `un push order-created est route vers le canal ventes`() {
        assertEquals(NotificationChannels.CHANNEL_SALES, NotificationChannels.channelForEvent("order.created"))
    }

    @Test
    fun `un push sav-message est route vers le canal message SAV`() {
        assertEquals(NotificationChannels.CHANNEL_SAV_MESSAGE, NotificationChannels.channelForEvent("sav.message"))
    }

    @Test
    fun `un push review-pending est route vers le canal avis a moderer`() {
        assertEquals(NotificationChannels.CHANNEL_REVIEW_PENDING, NotificationChannels.channelForEvent("review.pending"))
    }

    @Test
    fun `un event inconnu ou absent retombe sur le canal par defaut`() {
        assertEquals(NotificationChannels.CHANNEL_DEFAULT, NotificationChannels.channelForEvent("event.inconnu"))
        assertEquals(NotificationChannels.CHANNEL_DEFAULT, NotificationChannels.channelForEvent(null))
    }

    // ─── Paramètres des canaux créés ────────────────────────────────────────────

    @Test
    fun `le canal message SAV est cree en importance haute avec un son dedie`() {
        NotificationChannels.ensureAllChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationChannels.CHANNEL_SAV_MESSAGE)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(NotificationChannels.savMessageAlertSoundUri(context), channel.sound)
        assertTrue(channel.shouldVibrate())
    }

    @Test
    fun `le canal avis a moderer est cree en importance par defaut sans son personnalise`() {
        NotificationChannels.ensureAllChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationChannels.CHANNEL_REVIEW_PENDING)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        // Son système (pas de setSound explicite) : jamais le son dédié du canal message SAV.
        assertTrue(channel.sound != NotificationChannels.savMessageAlertSoundUri(context))
    }

    @Test
    fun `le son du canal message SAV est distinct de la caisse et du paiement en panne`() {
        val savSound = NotificationChannels.savMessageAlertSoundUri(context)
        assertTrue(savSound != NotificationChannels.cashRegisterSoundUri(context))
        assertTrue(savSound != NotificationChannels.paymentAlertSoundUri(context))
    }
}
