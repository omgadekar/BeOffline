package com.beoffline.app

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BeOfflineApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Accountability liveness + protection self-check (no-ops when signed out).
        com.beoffline.app.accountability.HeartbeatWorker.schedule(this)
        // Flush anything still queued from an offline session.
        com.beoffline.app.accountability.OutboxWorker.enqueue(this)
    }
}
