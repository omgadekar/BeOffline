package com.beoffline.app

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.beoffline.app.support.CrashReportingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BeOfflineApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var crashReporting: CrashReportingManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Before anything else can crash: whatever the person chose in Settings
        // wins over the manifest default.
        crashReporting.apply()
        // Accountability liveness + protection self-check (no-ops when signed out).
        com.beoffline.app.accountability.HeartbeatWorker.schedule(this)
        // Flush anything still queued from an offline session.
        com.beoffline.app.accountability.OutboxWorker.enqueue(this)
    }
}
