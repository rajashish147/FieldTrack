package com.fieldtrack.app.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.EmployeeRequestOwner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

// ─── SessionRecoveryWorker ────────────────────────────────────────────────────
// Runs every 15 minutes (and on BOOT via BootReceiver).
// Flushes saved GPS and reconciles a running service with the server shift.
// A visible activity starts a replacement service when the app is reopened.
// ─────────────────────────────────────────────────────────────────────────────

@HiltWorker
class SessionRecoveryWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: ApiService,
    private val sessionManager: SessionManager,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG       = "SessionRecovery"
        const val WORK_NAME         = "ft_session_recovery"
        private const val IMMEDIATE_WORK_NAME = "ft_session_recovery_immediate"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SessionRecoveryWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<SessionRecoveryWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val auth = sessionManager.getSession()
            val owner = auth?.owner ?: return Result.success()
            if (auth.role != "EMPLOYEE") return Result.success()
            LocationSyncWorker.flush(context)
            val tracking = LocationForegroundService.activeShift.value ?: return Result.success()
            if (tracking.owner != owner) return Result.success()
            val response = api.getMySessions(limit = 1, status = "active", owner = EmployeeRequestOwner(owner))
            if (!response.isSuccessful) {
                return if (response.code() == 401 || response.code() == 429 || response.code() >= 500) Result.retry() else Result.success()
            }
            val activeShift = response.body()?.data ?: return Result.retry()
            if (activeShift.firstOrNull()?.id != tracking.sessionId) {
                LocationForegroundService.stop(context, tracking)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Recovery check failed", e)
            Result.retry()
        }
    }
}
