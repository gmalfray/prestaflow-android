package com.rebuildit.prestaflow.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rebuildit.prestaflow.core.notifications.FcmRegistrationManager
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
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
                    } catch (e: IOException) {
                        Timber.e(e, "Network error refreshing order $orderId from push")
                    } catch (e: HttpException) {
                        Timber.e(e, "HTTP error refreshing order $orderId from push (code=${e.code()})")
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
