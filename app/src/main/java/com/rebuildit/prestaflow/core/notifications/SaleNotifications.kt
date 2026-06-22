package com.rebuildit.prestaflow.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rebuildit.prestaflow.R

/**
 * Canal et affichage des notifications de vente (« Nouvelle commande »), avec un
 * son de caisse par défaut (res/raw/cash_register.mp3, Pixabay — licence libre).
 *
 * Sur Android 8+, le son est une propriété du CANAL, fixée à sa création :
 * l'utilisateur peut le changer via les réglages système du canal
 * (appui long sur la notif → Paramètres → son), ce qui couvre le « choix du son ».
 */
object SaleNotifications {
    // v2 : le son d'un canal est figé à sa création (immuable). On change d'ID à chaque
    // changement de son pour forcer la recréation du canal avec le nouveau son.
    const val CHANNEL_ID = "sales_v2"

    private fun soundUri(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.cash_register}")

    /** Crée le canal « Ventes » avec le son de caisse (idempotent). À appeler au démarrage. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val attributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_sales_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_sales_desc)
                setSound(soundUri(context), attributes)
                enableVibration(true)
            }
        manager.createNotificationChannel(channel)
    }

    /**
     * Affiche une notification de vente. No-op si la permission notifications manque
     * (Android 13+). Fonctionne aussi quand l'app est au premier plan.
     */
    fun show(
        context: Context,
        title: String?,
        body: String?,
        orderId: Long?,
    ) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title ?: context.getString(R.string.notif_sale_default_title))
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Son explicite pour Android < 8 (sur 8+, c'est le canal qui décide).
                .setSound(soundUri(context))
                .build()

        val notificationId = orderId?.toInt() ?: (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
