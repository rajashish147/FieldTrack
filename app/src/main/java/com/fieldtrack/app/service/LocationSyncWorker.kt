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
        const val MAX_WORKER_ATTEMPTS = 5  // Align with repository MAX_SYNC_ATTEMPTS

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)  // Don't drain battery on low battery
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<LocationSyncWorker>(
                Constants.SYNC_PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,  // Initial backoff: 30 seconds
                    TimeUnit.SECONDS
                )
                .addTag(Constants.WORK_SYNC_LOCATIONS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.WORK_SYNC_LOCATIONS,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't restart if already running
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
            Log.d(TAG, "Starting sync work (attempt ${runAttemptCount + 1}/$MAX_WORKER_ATTEMPTS)")

            // Sync locations with hardened state machine
            val locationResult = locationRepository.syncUnsyncedLocations()
            
            when (locationResult) {
                is NetworkResult.Success -> {
                    val syncResult = locationResult.data
                    Log.d(TAG, "Location sync: ${syncResult.synced} synced, " +
                            "${syncResult.failed} failed, ${syncResult.abandoned} abandoned")
                    
                    // Log warning if locations were abandoned
                    if (syncResult.abandoned > 0) {
                        Log.w(TAG, "${syncResult.abandoned} locations abandoned (too many failures)")
                    }
                }
                is NetworkResult.Error -> {
                    Log.w(TAG, "Location sync failed: ${locationResult.message}")
                }
                else -> {
                    // Handle Loading or other states (shouldn't happen in sync context)
                    Log.w(TAG, "Location sync returned unexpected state: $locationResult")
                }
            }

            // Sync reports and expenses
            val reportResult = reportRepository.syncUnsyncedReports()
            val expenseResult = expenseRepository.syncUnsyncedExpenses()

            // Cleanup old synced data
            cleanupOldData()

            // Determine result based on all sync operations
            val allSuccess = locationResult is NetworkResult.Success &&
                    reportResult is NetworkResult.Success &&
                    expenseResult is NetworkResult.Success

            if (allSuccess) {
                Log.d(TAG, "All syncs completed successfully")
                Result.success()
            } else {
                // Retry with exponential backoff
                if (runAttemptCount < MAX_WORKER_ATTEMPTS) {
                    Log.w(TAG, "Some syncs failed, will retry (attempt ${runAttemptCount + 1}/$MAX_WORKER_ATTEMPTS)")
                    Result.retry()
                } else {
                    // Give up after max attempts
                    // Note: Individual locations may be in ABANDONED state in the database
                    // This just stops the WorkManager from retrying the entire job
                    Log.e(TAG, "Max worker attempts ($MAX_WORKER_ATTEMPTS) reached, giving up")
                    Log.e(TAG, "Check database for ABANDONED locations that need manual intervention")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync crashed: ${e.message}", e)
            
            // Retry on crash, but respect max attempts
            if (runAttemptCount < MAX_WORKER_ATTEMPTS) {
                Log.w(TAG, "Will retry after crash (attempt ${runAttemptCount + 1}/$MAX_WORKER_ATTEMPTS)")
                Result.retry()
            } else {
                Log.e(TAG, "Max worker attempts reached after crash, giving up")
                Result.failure()
            }
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
            Log.e(TAG, "Error cleaning up old data: ${e.message}", e)
        }
    }
}
