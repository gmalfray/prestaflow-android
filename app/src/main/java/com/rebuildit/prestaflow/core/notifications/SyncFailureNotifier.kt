package com.rebuildit.prestaflow.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Affiche une notification système (canal [NotificationChannels.CHANNEL_DEFAULT]) quand une
 * tâche de la file offline est abandonnée après une réponse HTTP non réessayable (400/422…).
 *
 * Une seule notification (ID fixe) est réutilisée : plusieurs tâches abandonnées consécutives
 * mettent à jour le même message plutôt que de spammer la barre de notifications.
 */
@Singleton
class SyncFailureNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SyncFailureNotifierContract {
        override fun notifyTaskDropped(
            task: PendingSyncTask,
            httpCode: Int,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val resourceLabel = task.resourceType ?: task.endpoint
            val notification =
                NotificationCompat.Builder(context, NotificationChannels.CHANNEL_DEFAULT)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.sync_failure_notif_title))
                    .setContentText(context.getString(R.string.sync_failure_notif_body, resourceLabel, httpCode))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        private companion object {
            const val NOTIFICATION_ID = 0x53_59_4E_43 // "SYNC" en ASCII, ID stable pour dédupliquer/remplacer
        }
    }
