package com.rebuildit.prestaflow.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import com.rebuildit.prestaflow.R

/**
 * Centralise la création de tous les canaux de notification de PrestaFlow.
 *
 * Trois canaux métier :
 *  - [CHANNEL_SALES]          : ventes (son caisse — `cash_register.mp3`), importance HIGH.
 *  - [CHANNEL_ORDER_STATUS]   : changements de statut, son système par défaut, importance DEFAULT.
 *  - [CHANNEL_ORDER_SHIPPING] : mises à jour d'expédition, son système par défaut, importance DEFAULT.
 *
 * Un canal filet de sécurité :
 *  - [CHANNEL_DEFAULT] : push background sans `channel_id` explicite, importance DEFAULT, son système.
 *
 * Règle de routage foreground : [channelForEvent].
 */
object NotificationChannels {
    /** Canal ventes — son caisse (immuable à la création du canal). */
    const val CHANNEL_SALES = "sales_v2"

    /** Canal changements de statut de commande — son système. */
    const val CHANNEL_ORDER_STATUS = "order_status"

    /** Canal mises à jour d'expédition — son système. */
    const val CHANNEL_ORDER_SHIPPING = "order_shipping"

    /**
     * Canal par défaut (background fallback) — son système, sans caisse.
     * Utilisé comme `default_notification_channel_id` dans le manifeste.
     */
    const val CHANNEL_DEFAULT = "default_alerts"

    // ── Routing ────────────────────────────────────────────────────────────────

    /**
     * Retourne le `channel_id` à utiliser pour un push reçu en foreground
     * selon la valeur de `data["event"]`.
     * Pour tout event inconnu ou absent, retourne [CHANNEL_DEFAULT] (sobre).
     */
    fun channelForEvent(event: String?): String =
        when (event) {
            "order.created" -> CHANNEL_SALES
            "order.status.changed" -> CHANNEL_ORDER_STATUS
            "order.shipping.updated" -> CHANNEL_ORDER_SHIPPING
            else -> CHANNEL_DEFAULT
        }

    // ── Création (idempotent) ──────────────────────────────────────────────────

    /** Crée tous les canaux au démarrage de l'app (idempotent, Android 8+). */
    fun ensureAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        ensureSalesChannel(context, manager)
        ensureOrderStatusChannel(context, manager)
        ensureOrderShippingChannel(context, manager)
        ensureDefaultChannel(context, manager)
    }

    // ── Accès au son caisse (pour compatibilité < Android 8) ──────────────────

    fun cashRegisterSoundUri(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.cash_register}")

    // ── Privé ─────────────────────────────────────────────────────────────────

    private fun ensureSalesChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_SALES) != null) return
        val attributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
        val channel =
            NotificationChannel(
                CHANNEL_SALES,
                context.getString(R.string.notif_channel_sales_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_sales_desc)
                setSound(cashRegisterSoundUri(context), attributes)
                enableVibration(true)
            }
        manager.createNotificationChannel(channel)
    }

    private fun ensureOrderStatusChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_ORDER_STATUS) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ORDER_STATUS,
                context.getString(R.string.notif_channel_order_status_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_order_status_desc)
            }
        manager.createNotificationChannel(channel)
    }

    private fun ensureOrderShippingChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_ORDER_SHIPPING) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ORDER_SHIPPING,
                context.getString(R.string.notif_channel_order_shipping_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_order_shipping_desc)
            }
        manager.createNotificationChannel(channel)
    }

    private fun ensureDefaultChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_DEFAULT) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_DEFAULT,
                context.getString(R.string.notif_channel_default_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_default_desc)
            }
        manager.createNotificationChannel(channel)
    }
}
