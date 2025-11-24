package com.rebuildit.prestaflow.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PrestaFlowFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var ordersRepository: OrdersRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("New FCM token: $token")
        scope.launch {
            try {
                ordersRepository.registerToken(token)
            } catch (e: Exception) {
                Timber.e(e, "Failed to register new token")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("Message received from: ${message.from}")

        // Check if message contains data payload.
        if (message.data.isNotEmpty()) {
            Timber.d("Message data payload: ${message.data}")
            // Handle data payload here if needed.
            // For now, we rely on the notification payload handled by the system tray
            // or we could trigger a data refresh.
            val orderId = message.data["order_id"]?.toLongOrNull()
            if (orderId != null) {
                scope.launch {
                    try {
                        ordersRepository.refreshOrder(orderId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to refresh order $orderId from push")
                    }
                }
            }
        }

        // Check if message contains notification payload.
        message.notification?.let {
            Timber.d("Message Notification Body: ${it.body}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
