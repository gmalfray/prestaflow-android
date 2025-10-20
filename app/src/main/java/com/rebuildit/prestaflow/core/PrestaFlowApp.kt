package com.rebuildit.prestaflow.core

import android.app.Application
import android.util.Log
import com.rebuildit.prestaflow.BuildConfig
import com.rebuildit.prestaflow.core.notifications.FcmRegistrationManager
import com.rebuildit.prestaflow.core.sync.SyncOrchestrator
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PrestaFlowApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncOrchestrator: SyncOrchestrator

    @Inject
    lateinit var notificationRegistrationManager: FcmRegistrationManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        syncOrchestrator.start()
        notificationRegistrationManager.initialize()
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
    }
}
