package com.fieldtrack.app.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import com.fieldtrack.app.util.Constants
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class LocationSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    companion object {
        const val TAG = "LocationSyncWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<LocationSyncWorker>(
                Constants.SYNC_PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .addTag(Constants.WORK_SYNC_LOCATIONS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.WORK_SYNC_LOCATIONS,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "Periodic sync scheduled every ${Constants.SYNC_PERIODIC_INTERVAL_MIN} minutes")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(Constants.WORK_SYNC_LOCATIONS)
            Log.d(TAG, "Periodic sync cancelled")
        }
    }

    private val locationRepository: LocationRepository by inject()
    private val reportRepository: ReportRepository by inject()
    private val expenseRepository: ExpenseRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting sync work")

            val locationResult = locationRepository.syncUnsyncedLocations()
            val reportResult = reportRepository.syncUnsyncedReports()
            val expenseResult = expenseRepository.syncUnsyncedExpenses()

            // Cleanup old synced locations
            cleanupOldData()

            val allSuccess = locationResult is NetworkResult.Success &&
                    reportResult is NetworkResult.Success &&
                    expenseResult is NetworkResult.Success

            if (allSuccess) {
                Log.d(TAG, "Sync completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "Some sync tasks failed, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    private suspend fun cleanupOldData() {
        try {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.add(Calendar.DAY_OF_YEAR, -Constants.LOCATION_CLEANUP_AGE_DAYS)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val cutoffTimestamp = sdf.format(calendar.time)
            locationRepository.cleanupOldSyncedLocations(cutoffTimestamp)
            Log.d(TAG, "Cleaned up synced locations older than ${Constants.LOCATION_CLEANUP_AGE_DAYS} days")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old data", e)
        }
    }
}
