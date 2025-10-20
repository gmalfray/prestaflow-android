package com.rebuildit.prestaflow.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class PrestaFlowMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var registrationManager: FcmRegistrationManager


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("Received new FCM token")
        registrationManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("FCM message received: %s", message.data)
        // Foreground handling will be implemented in Phase 5 step 4.
    }
}
