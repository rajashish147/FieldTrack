package com.fieldtrack.app.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.fieldtrack.app.core.crash.CrashReporter
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.CrashReportRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CrashUploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: ApiService,
    private val sessionManager: SessionManager,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CrashUploadWorker"
        const val WORK_NAME = "ft_crash_upload"
        private const val MAX_ATTEMPTS = 5
        private const val MAX_REPORT_CHARS = 200_000

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<CrashUploadWorker>()
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
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        if (sessionManager.getToken() == null) {
            Log.d(TAG, "No session — crash upload deferred until next login")
            return Result.success()
        }

        val reports = CrashReporter.getPendingReports(context)
        if (reports.isEmpty()) return Result.success()

        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.w(TAG, "Crash upload retry limit reached; reports remain for next app start")
            return Result.success()
        }

        for (report in reports) {
            try {
                val raw = report.readText().take(MAX_REPORT_CHARS)
                val resp = api.uploadCrashReport(
                    CrashReportRequest(
                        fileName = report.name,
                        rawReport = raw,
                    )
                )

                when {
                    resp.isSuccessful -> {
                        if (!report.delete()) {
                            Log.w(TAG, "Uploaded but failed to delete ${report.name}")
                        }
                    }
                    resp.code() == 401 -> return Result.success()
                    resp.code() in 400..499 -> {
                        Log.e(TAG, "Discarding invalid crash report ${report.name}: HTTP ${resp.code()}")
                        report.delete()
                    }
                    else -> return Result.retry()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Crash upload failed for ${report.name}: ${e.message}")
                return Result.retry()
            }
        }

        return Result.success()
    }
}
