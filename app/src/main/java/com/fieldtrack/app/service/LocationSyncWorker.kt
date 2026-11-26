package com.fieldtrack.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LocationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploader: LocationUploader,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val PERIODIC_WORK = "ft_location_sync"
        private const val FLUSH_WORK = "ft_location_flush"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LocationSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC_WORK)
                cancelUniqueWork(FLUSH_WORK)
            }
        }

        fun stopPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }

        fun flush(context: Context) {
            val request = OneTimeWorkRequestBuilder<LocationSyncWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(FLUSH_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        uploader.prune()
        return if (uploader.drain()) Result.success() else Result.retry()
    }
}
