package com.fieldtrack.app.data.repository

import android.util.Log
import com.fieldtrack.app.data.local.dao.ReportDao
import com.fieldtrack.app.data.local.entity.ReportEntity
import com.fieldtrack.app.data.local.entity.SyncState
import com.fieldtrack.app.data.model.Report
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.ReportApi
import com.fieldtrack.app.data.remote.dto.ReportDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReportRepository(
    private val reportDao: ReportDao,
    private val reportApi: ReportApi,
    private val sessionManager: SessionManager
) {
    companion object {
        const val TAG = "ReportRepository"
        const val MAX_SYNC_ATTEMPTS = 5
        const val SYNC_TIMEOUT_MS = 5 * 60 * 1000L  // 5 minutes
        const val BATCH_SIZE = 50
        private const val MAX_ERROR_MESSAGE_LENGTH = 255
        private const val MAX_ERROR_LIST_SIZE = 10
    }

    suspend fun saveReportLocally(report: Report) {
        withContext(Dispatchers.IO) {
            reportDao.insert(ReportEntity.fromModel(report))
        }
    }

    fun getReportsByUser(userId: String): Flow<List<Report>> =
        reportDao.getReportsByUser(userId).map { entities ->
            entities.map { it.toModel() }
        }

    /**
     * Hardened sync with deterministic state transitions.
     * Implements: LOCAL_ONLY → SYNCING → SYNCED | FAILED | ABANDONED
     */
    suspend fun syncUnsyncedReports(): SyncResult =
        withContext(Dispatchers.IO) {
            val userId = sessionManager.userId 
                ?: return@withContext SyncResult.Failed("No user ID", 0)
            
            try {
                // 1. Reset stale SYNCING states (crash recovery)
                val timeoutThreshold = System.currentTimeMillis() - SYNC_TIMEOUT_MS
                reportDao.resetStaleSyncingState(timeoutThreshold)
                
                // 2. Get syncable reports
                val toSync = reportDao.getSyncableReports(
                    userId = userId,
                    maxAttempts = MAX_SYNC_ATTEMPTS,
                    batchSize = BATCH_SIZE
                )
                
                if (toSync.isEmpty()) {
                    Log.d(TAG, "No reports to sync")
                    return@withContext SyncResult.Success(0, System.currentTimeMillis())
                }
                
                Log.d(TAG, "Syncing ${toSync.size} reports")
                
                // 3. Mark as SYNCING
                val ids = toSync.map { it.id }
                reportDao.updateSyncState(ids, SyncState.SYNCING)
                
                // 4. Sync individually to handle partial failures
                var syncedCount = 0
                var failedCount = 0
                var abandonedCount = 0
                val errors = mutableListOf<String>()
                
                for (entity in toSync) {
                    try {
                        val dto = ReportDto.fromModel(entity.toModel())
                        val response = reportApi.insertReport(dto)
                        
                        if (response.isSuccessful) {
                            reportDao.markAsSyncedNew(listOf(entity.id))
                            syncedCount++
                        } else {
                            val errorMsg = "HTTP ${response.code()}"
                            errors.add(errorMsg)
                            val (failed, abandoned) = handleSyncFailure(entity, errorMsg)
                            failedCount += failed
                            abandonedCount += abandoned
                        }
                    } catch (e: Exception) {
                        val errorMsg = e.message ?: "Unknown error"
                        errors.add(errorMsg)
                        val (failed, abandoned) = handleSyncFailure(entity, errorMsg)
                        failedCount += failed
                        abandonedCount += abandoned
                    }
                }
                
                Log.d(TAG, "Report sync complete: $syncedCount synced, $failedCount failed, $abandonedCount abandoned")
                
                // 5. Return appropriate result
                when {
                    syncedCount == toSync.size -> SyncResult.Success(syncedCount, System.currentTimeMillis())
                    syncedCount > 0 -> SyncResult.Partial(syncedCount, failedCount, abandonedCount, errors.take(MAX_ERROR_LIST_SIZE))
                    else -> SyncResult.Failed("All reports failed to sync", failedCount + abandonedCount, errors.take(MAX_ERROR_LIST_SIZE))
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Log.e(TAG, "Sync exception: $errorMsg", e)
                SyncResult.Failed(errorMsg, 0)
            }
        }
    
    /**
     * Handle sync failure for a single report.
     * 
     * @return Pair of (failed count, abandoned count)
     */
    private suspend fun handleSyncFailure(
        report: ReportEntity,
        errorMessage: String
    ): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val newAttempts = report.syncAttempts + 1
        val newState = if (newAttempts >= MAX_SYNC_ATTEMPTS) {
            SyncState.ABANDONED
        } else {
            SyncState.FAILED
        }
        
        reportDao.updateSyncFailure(
            id = report.id,
            state = newState,
            attempts = newAttempts,
            errorMessage = errorMessage.take(MAX_ERROR_MESSAGE_LENGTH),
            attemptedAt = now
        )
        
        return if (newState == SyncState.ABANDONED) {
            Log.w(TAG, "Report ${report.id} abandoned after $newAttempts attempts")
            Pair(0, 1)
        } else {
            Pair(1, 0)
        }
    }

    suspend fun fetchRemoteReports(userId: String): NetworkResult<List<Report>> =
        withContext(Dispatchers.IO) {
            try {
                val response = reportApi.getReportsByUser(userId = "eq.$userId")
                if (response.isSuccessful && response.body() != null) {
                    val reports = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(reports)
                } else {
                    NetworkResult.Error("Failed to fetch reports")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun hasReportForToday(userId: String, date: String): Boolean =
        withContext(Dispatchers.IO) {
            reportDao.getReportByUserAndDate(userId, date) != null
        }

    suspend fun fetchAllReports(): NetworkResult<List<Report>> =
        withContext(Dispatchers.IO) {
            try {
                val response = reportApi.getAllReports()
                if (response.isSuccessful && response.body() != null) {
                    val reports = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(reports)
                } else {
                    NetworkResult.Error("Failed to fetch reports")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }
    
    suspend fun getAbandonedReports(): List<Report> = withContext(Dispatchers.IO) {
        val userId = sessionManager.userId ?: return@withContext emptyList()
        reportDao.getAbandonedReports(userId).map { it.toModel() }
    }
    
    suspend fun retryAbandonedReports(reportIds: List<String>) {
        withContext(Dispatchers.IO) {
            reportDao.retryAbandonedReports(reportIds)
        }
    }
}
