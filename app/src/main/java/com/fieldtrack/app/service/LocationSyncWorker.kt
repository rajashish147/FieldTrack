package com.fieldtrack.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocationSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    companion object {
        const val TAG = "LocationSyncWorker"
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
}
