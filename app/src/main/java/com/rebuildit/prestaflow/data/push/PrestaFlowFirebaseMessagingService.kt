package com.rebuildit.prestaflow.data.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.notifications.FcmRegistrationManager
import com.rebuildit.prestaflow.core.notifications.NotificationChannels
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class PrestaFlowFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var ordersRepository: OrdersRepository

    @Inject
    lateinit var registrationManager: FcmRegistrationManager

    private val job = SupervisorJob()

    @Suppress("InjectDispatcher") // FirebaseMessagingService est un service Android non injectable par Hilt constructor
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("New FCM token received")
        // Registration is routed through the notifications/devices endpoint.
        registrationManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("Message received from: ${message.from}")

        val event = message.data["event"]
        val orderId = message.data["order_id"]?.toLongOrNull()

        // Rafraîchit la commande référencée par le push (si présente).
        if (orderId != null) {
            scope.launch {
                try {
                    ordersRepository.refreshOrder(orderId)
                } catch (e: IOException) {
                    Timber.e(e, "Network error refreshing order $orderId from push")
                } catch (e: HttpException) {
                    Timber.e(e, "HTTP error refreshing order $orderId from push (code=${e.code()})")
                }
            }
        }

        // Affichage foreground : routage par event → canal (et donc son) adapté.
        // En arrière-plan, le système gère lui-même via default_notification_channel_id (manifeste).
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title != null || body != null) {
            showNotification(event = event, title = title, body = body, orderId = orderId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    // ── Affichage ──────────────────────────────────────────────────────────────

    private fun showNotification(
        event: String?,
        title: String?,
        body: String?,
        orderId: Long?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channelId = NotificationChannels.channelForEvent(event)

        val builder =
            NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title ?: applicationContext.getString(R.string.notif_sale_default_title))
                .setContentText(body)
                .setAutoCancel(true)

        // Priorité et son explicite (Android < 8) selon le canal.
        if (channelId == NotificationChannels.CHANNEL_SALES) {
            builder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Son explicite pour Android < 8 (sur 8+ c'est le canal qui décide).
                .setSound(NotificationChannels.cashRegisterSoundUri(applicationContext))
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Son système par défaut pour Android < 8.
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }

        val notificationId = orderId?.toInt() ?: (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
    }
}
