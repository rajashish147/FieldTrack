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
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import com.fieldtrack.app.data.repository.SyncResult
import com.fieldtrack.app.util.Constants
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Periodic auto-sync worker for location tracking.
 * 
 * Runs every 15 minutes when:
 * - User is checked in
 * - Unsynced locations exist
 * - Network is connected
 * 
 * Constraints:
 * - Prevents parallel sync executions (concurrency guard)
 * - Handles failures safely (no crash)
 * - Uses existing LocationRepository.sync()
 * 
 * Lifecycle:
 * - Scheduled on check-in
 * - Cancelled on check-out
 */
class LocationSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    companion object {
        const val TAG = "LocationSyncWorker"
        const val MAX_WORKER_ATTEMPTS = 5  // Align with repository MAX_SYNC_ATTEMPTS
        
        // Concurrency guard: Prevents parallel sync executions
        // AtomicBoolean ensures thread-safe check-and-set operation
        private val isSyncRunning = AtomicBoolean(false)

        /**
         * Schedule periodic auto-sync.
         * 
         * Called on check-in to start 15-minute periodic sync.
         * Uses KEEP policy to prevent duplicate workers.
         * 
         * @param context Application context
         */
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

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Periodic sync scheduled every ${Constants.SYNC_PERIODIC_INTERVAL_MIN} minutes")
            }
        }

        /**
         * Cancel periodic auto-sync.
         * 
         * Called on check-out to stop sync worker.
         * 
         * @param context Application context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(Constants.WORK_SYNC_LOCATIONS)
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Periodic sync cancelled")
            }
        }
    }

    private val locationRepository: LocationRepository by inject()
    private val reportRepository: ReportRepository by inject()
    private val expenseRepository: ExpenseRepository by inject()
    private val sessionManager: SessionManager by inject()

    override suspend fun doWork(): Result {
        return try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Starting sync work (attempt ${runAttemptCount + 1}/$MAX_WORKER_ATTEMPTS)")
            }
            
            // CONSTRAINT 1: Check if user is checked in
            // If user checked out mid-sync, abort gracefully
            if (!sessionManager.isCheckedIn) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "User not checked in, skipping sync")
                }
                return Result.success()
            }
            
            // CONSTRAINT 2: Check if unsynced locations exist
            // Avoid unnecessary work if nothing to sync
            val unsyncedCount = locationRepository.getUnsyncedCount()
            if (unsyncedCount == 0) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "No unsynced locations, skipping sync")
                }
                return Result.success()
            }
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Found $unsyncedCount unsynced locations, starting sync")
            }
            
            // CONSTRAINT 3: Concurrency guard
            // Prevent parallel sync executions (idempotent behavior)
            if (!isSyncRunning.compareAndSet(false, true)) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Sync already running, skipping duplicate execution")
                }
                return Result.success()
            }
            
            try {
                // Sync locations with hardened state machine
                val locationResult = locationRepository.syncUnsyncedLocations()
                
                when (locationResult) {
                    is SyncResult.Success -> {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Location sync success: ${locationResult.synced} synced")
                        }
                    }
                    is SyncResult.Partial -> {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Location sync partial: ${locationResult.synced} synced, " +
                                    "${locationResult.failed} failed, ${locationResult.abandoned} abandoned")
                        }
                        
                        if (locationResult.abandoned > 0 && BuildConfig.DEBUG) {
                            Log.w(TAG, "${locationResult.abandoned} locations abandoned (too many failures)")
                        }
                    }
                    is SyncResult.Failed -> {
                        // Production logging: Structured error for crash monitoring
                        Log.e(TAG, "Location sync failed: ${locationResult.reason}, attempt: ${runAttemptCount + 1}/$MAX_WORKER_ATTEMPTS")
                        
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "Location sync failed: ${locationResult.reason}")
                        }
                        return Result.retry()
                    }
                }

                // Sync reports and expenses
                val reportResult = reportRepository.syncUnsyncedReports()
                val expenseResult = expenseRepository.syncUnsyncedExpenses()
                
                if (BuildConfig.DEBUG) {
                    when (reportResult) {
                        is SyncResult.Success -> Log.d(TAG, "Report sync success: ${reportResult.synced} synced")
                        is SyncResult.Partial -> Log.w(TAG, "Report sync partial: ${reportResult.synced} synced, ${reportResult.failed} failed")
                        is SyncResult.Failed -> Log.e(TAG, "Report sync failed: ${reportResult.reason}")
                    }
                    
                    when (expenseResult) {
                        is SyncResult.Success -> Log.d(TAG, "Expense sync success: ${expenseResult.synced} synced")
                        is SyncResult.Partial -> Log.w(TAG, "Expense sync partial: ${expenseResult.synced} synced, ${expenseResult.failed} failed")
                        is SyncResult.Failed -> Log.e(TAG, "Expense sync failed: ${expenseResult.reason}")
                    }
                }

                // Cleanup old synced data
                cleanupOldData()

                // Determine result based on all sync operations
                val allSuccess = locationResult is SyncResult.Success &&
                        reportResult is SyncResult.Success &&
                        expenseResult is SyncResult.Success

                if (allSuccess) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "All syncs completed successfully")
                    }
                    Result.success()
                } else {
                    // Retry with exponential backoff
                    if (runAttemptCount < MAX_WORKER_ATTEMPTS) {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Some syncs failed, will retry (attempt ${runAttemptCount + 1}/$MAX_WORKER_ATTEMPTS)")
                        }
                        Result.retry()
                    } else {
                        // Give up after max attempts
                        // Note: Individual locations may be in ABANDONED state in the database
                        // This just stops the WorkManager from retrying the entire job
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "Max worker attempts ($MAX_WORKER_ATTEMPTS) reached, giving up")
                            Log.e(TAG, "Check database for ABANDONED locations that need manual intervention")
                        }
                        Result.failure()
                    }
                }
            } finally {
                // CRITICAL: Always release concurrency guard
                isSyncRunning.set(false)
            }
        } catch (e: Exception) {
            // CONSTRAINT 4: Handle failure safely (no crash)
            // Production logging: Structured error for crash monitoring
            // Security: Don't interpolate exception messages (may contain sensitive data)
            Log.e(TAG, "Sync worker crashed, attempt: ${runAttemptCount + 1}/$MAX_WORKER_ATTEMPTS", e)
            
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Sync crashed: ${e.message}", e)
            }
            
            // Release concurrency guard on exception
            isSyncRunning.set(false)
            
            // Retry on crash, but respect max attempts
            if (runAttemptCount < MAX_WORKER_ATTEMPTS) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Will retry after crash (attempt ${runAttemptCount + 1}/$MAX_WORKER_ATTEMPTS)")
                }
                Result.retry()
            } else {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Max worker attempts reached after crash, giving up")
                }
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
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cleaned up synced locations older than ${Constants.LOCATION_CLEANUP_AGE_DAYS} days")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error cleaning up old data: ${e.message}", e)
            }
        }
    }
}
