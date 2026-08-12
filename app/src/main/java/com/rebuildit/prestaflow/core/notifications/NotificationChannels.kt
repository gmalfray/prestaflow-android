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
 * Canaux métier :
 *  - [CHANNEL_SALES]          : ventes (son caisse — `cash_register.mp3`), importance HIGH.
 *  - [CHANNEL_ORDER_STATUS]   : changements de statut, son système par défaut, importance DEFAULT.
 *  - [CHANNEL_ORDER_SHIPPING] : mises à jour d'expédition, son système par défaut, importance DEFAULT.
 *  - [CHANNEL_STOCK_LOW]      : alertes stock faible, son système par défaut, importance DEFAULT.
 *  - [CHANNEL_PAYMENT_ERROR]  : panne de paiement, son dédié, importance HIGH.
 *  - [CHANNEL_SAV_MESSAGE]    : nouveau message SAV, son dédié, importance HIGH.
 *  - [CHANNEL_REVIEW_PENDING] : nouvel avis à modérer, son système par défaut, importance DEFAULT.
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

    /** Canal alertes stock faible — son système. */
    const val CHANNEL_STOCK_LOW = "stock_low"

    // Motif de vibration du canal panne de paiement (ms) : silence, vibration, silence, vibration —
    // cf. ensurePaymentErrorChannel. Nommé pour satisfaire detekt MagicNumber.
    private const val PAYMENT_ERROR_VIBRATION_INITIAL_DELAY_MS = 0L
    private const val PAYMENT_ERROR_VIBRATION_PULSE_MS = 250L
    private const val PAYMENT_ERROR_VIBRATION_GAP_MS = 150L

    /**
     * Canal panne de paiement — importance HAUTE : la boutique ne peut plus encaisser,
     * ça doit sortir de la poche même noyé dans les autres notifications.
     */
    const val CHANNEL_PAYMENT_ERROR = "payment_error"

    /**
     * Canal message SAV — importance HAUTE : une cliente attend une réponse, c'est la
     * notification utile au quotidien. Son propre, distinct de la caisse (qui signifierait
     * l'inverse : une vente, pas une attente).
     */
    const val CHANNEL_SAV_MESSAGE = "sav_message"

    /**
     * Canal avis à modérer — importance DEFAULT, son système : de l'information, pas une
     * urgence (catégorie désactivée par défaut, cohérente avec le réglage boutique).
     */
    const val CHANNEL_REVIEW_PENDING = "review_pending"

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
            "stock.low" -> CHANNEL_STOCK_LOW
            "shop.payment.error" -> CHANNEL_PAYMENT_ERROR
            "sav.message" -> CHANNEL_SAV_MESSAGE
            "review.pending" -> CHANNEL_REVIEW_PENDING
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
        ensureStockLowChannel(context, manager)
        ensurePaymentErrorChannel(context, manager)
        ensureSavMessageChannel(context, manager)
        ensureReviewPendingChannel(context, manager)
        ensureDefaultChannel(context, manager)
    }

    // ── Accès au son caisse (pour compatibilité < Android 8) ──────────────────

    fun cashRegisterSoundUri(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.cash_register}")

    /** Son du canal « paiement en panne » — deux notes descendantes, cf. tools/sounds/. */
    fun paymentAlertSoundUri(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.payment_alert}")

    /** Son du canal « message SAV » — deux notes ascendantes, cf. tools/sounds/. */
    fun savMessageAlertSoundUri(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.sav_message_alert}")

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

    private fun ensureStockLowChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_STOCK_LOW) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_STOCK_LOW,
                context.getString(R.string.notif_channel_stock_low_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_stock_low_desc)
            }
        manager.createNotificationChannel(channel)
    }

    private fun ensurePaymentErrorChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_PAYMENT_ERROR) != null) return
        val attributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
        val channel =
            NotificationChannel(
                CHANNEL_PAYMENT_ERROR,
                context.getString(R.string.notif_channel_payment_error_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_payment_error_desc)
                // Son propre : ni la caisse (= vente encaissée, l'inverse du message), ni le son
                // système générique qu'on entend pour tout et n'importe quoi. ⚠️ Immuable une fois
                // le canal créé — le changer imposerait un CHANNEL_PAYMENT_ERROR v2.
                setSound(paymentAlertSoundUri(context), attributes)
                enableVibration(true)
                vibrationPattern =
                    longArrayOf(
                        PAYMENT_ERROR_VIBRATION_INITIAL_DELAY_MS,
                        PAYMENT_ERROR_VIBRATION_PULSE_MS,
                        PAYMENT_ERROR_VIBRATION_GAP_MS,
                        PAYMENT_ERROR_VIBRATION_PULSE_MS,
                    )
            }
        manager.createNotificationChannel(channel)
    }

    private fun ensureSavMessageChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_SAV_MESSAGE) != null) return
        val attributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
        val channel =
            NotificationChannel(
                CHANNEL_SAV_MESSAGE,
                context.getString(R.string.notif_channel_sav_message_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_sav_message_desc)
                // Son propre : ni la caisse (= vente encaissée, l'inverse du message d'une cliente
                // qui attend une réponse), ni le son système générique. ⚠️ Immuable une fois le
                // canal créé — le changer imposerait un CHANNEL_SAV_MESSAGE v2.
                setSound(savMessageAlertSoundUri(context), attributes)
                enableVibration(true)
            }
        manager.createNotificationChannel(channel)
    }

    private fun ensureReviewPendingChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_REVIEW_PENDING) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_REVIEW_PENDING,
                context.getString(R.string.notif_channel_review_pending_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_review_pending_desc)
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
