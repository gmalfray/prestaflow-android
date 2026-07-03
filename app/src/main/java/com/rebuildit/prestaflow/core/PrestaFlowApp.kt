package com.rebuildit.prestaflow.core

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rebuildit.prestaflow.BuildConfig
import com.rebuildit.prestaflow.core.notifications.FcmRegistrationManager
import com.rebuildit.prestaflow.core.notifications.NotificationChannels
import com.rebuildit.prestaflow.core.sync.SyncOrchestrator
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PrestaFlowApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncOrchestrator: SyncOrchestrator

    // Lazy (cf. TokenAuthenticator, même pattern) : un @Inject direct forcerait Hilt à construire
    // FcmRegistrationManager — donc AuthRepository/NotificationsRepository/ApiEndpointManager, donc
    // l'EncryptedSharedPreferences adossé au Keystore Android — de façon SYNCHRONE pendant
    // l'injection des champs de l'Application, laquelle a lieu sur le thread principal AVANT même
    // le corps de onCreate() (jank/ANR potentiel au cold start). Lazy + dispatch sur Dispatchers.IO
    // ci-dessous déplace ce coût hors du thread principal.
    @Inject
    lateinit var notificationRegistrationManager: Lazy<FcmRegistrationManager>

    @Suppress("InjectDispatcher") // Scope applicatif interne : pas de coroutine injectable au niveau Application
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        NotificationChannels.ensureAllChannels(this)
        syncOrchestrator.start()
        appScope.launch(Dispatchers.IO) {
            notificationRegistrationManager.get().initialize()
        }
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
    }
}
