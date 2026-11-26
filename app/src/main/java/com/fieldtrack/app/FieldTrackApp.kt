package com.fieldtrack.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fieldtrack.app.core.crash.CrashReporter
import com.fieldtrack.app.service.CrashUploadWorker
import com.fieldtrack.app.service.ExpenseRetryWorker
import com.fieldtrack.app.service.SessionRecoveryWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FieldTrackApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // WorkManager uses Hilt-aware factory — workers can inject dependencies
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            // Suppress verbose WorkManager logs in release; warnings only
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()

        // ── 1. Crash reporter (must be FIRST) ─────────────────────────────────
        // Registers uncaught exception handler.
        // Writes reports to app-private storage for next-launch upload.
        CrashReporter.init(this)

        // Log if there were previous crashes (for debug awareness)
        if (BuildConfig.DEBUG) {
            val pending = CrashReporter.getPendingReports(this)
            if (pending.isNotEmpty()) {
                Log.w("FieldTrackApp", "${pending.size} crash report(s) from previous session")
            }
        }

        // ── 2. Persistent background workers (idempotent — KEEP policy) ───────
        // Scheduled on every process start.
        // WorkManager KEEP policy prevents duplicates.
        SessionRecoveryWorker.schedule(this)
        ExpenseRetryWorker.schedule(this)
        CrashUploadWorker.schedule(this)
    }
}
