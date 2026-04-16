package com.fieldtrack.app.data.repository

import android.util.Log
import com.fieldtrack.app.data.local.dao.LocationDao
import com.fieldtrack.app.data.local.entity.LocationEntity
import com.fieldtrack.app.data.local.entity.SyncState
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.LocationApi
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.remote.dto.LocationDto
import com.fieldtrack.app.data.remote.dto.UpdateCheckInDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocationRepository(
    private val locationDao: LocationDao,
    private val locationApi: LocationApi,
    private val userApi: UserApi,
    private val sessionManager: SessionManager
) {
    companion object {
        const val TAG = "LocationRepository"
        const val MAX_SYNC_ATTEMPTS = 5
        const val SYNC_TIMEOUT_MS = 5 * 60 * 1000L  // 5 minutes
        const val BATCH_SIZE = 100
        private const val MAX_ERROR_MESSAGE_LENGTH = 255
    }

    /**
     * Dedicated sync scope that survives ViewModel cancellation.
     * CRITICAL: Final sync must complete even if UI is destroyed.
     * Uses SupervisorJob to prevent cancellation propagation.
     */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Update the current user's check-in status on the server */
    suspend fun updateCheckInStatus(isCheckedIn: Boolean): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val userId = sessionManager.userId 
                    ?: return@withContext NetworkResult.Error("No user ID")
                val response = userApi.updateCheckInStatus(
                    id = "eq.$userId",
                    body = mapOf("is_checked_in" to isCheckedIn)
                )
                if (response.isSuccessful) {
                    NetworkResult.Success(Unit)
                } else {
                    NetworkResult.Error("Failed to update check-in status", response.code())
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Network error")
            }
        }

    suspend fun saveLocationLocally(location: Location) {
        withContext(Dispatchers.IO) {
            locationDao.insert(LocationEntity.fromModel(location))
        }
    }

    /**
     * Hardened sync with deterministic state transitions.
     * Implements: LOCAL_ONLY → SYNCING → SYNCED | FAILED | ABANDONED
     */
    suspend fun syncUnsyncedLocations(): SyncResult =
        withContext(Dispatchers.IO) {
            val userId = sessionManager.userId 
                ?: return@withContext SyncResult.Failed("No user ID", 0)
            
            try {
                // 1. Reset stale SYNCING states (crash recovery)
                val timeoutThreshold = System.currentTimeMillis() - SYNC_TIMEOUT_MS
                locationDao.resetStaleSyncingState(timeoutThreshold)
                
                // 2. Get syncable locations (excludes SYNCING and ABANDONED)
                val toSync = locationDao.getSyncableLocations(
                    userId = userId,
                    maxAttempts = MAX_SYNC_ATTEMPTS,
                    batchSize = BATCH_SIZE
                )
                
                if (toSync.isEmpty()) {
                    Log.d(TAG, "No locations to sync")
                    return@withContext SyncResult.Success(0, System.currentTimeMillis())
                }
                
                Log.d(TAG, "Syncing ${toSync.size} locations")
                
                // 3. Mark as SYNCING (prevents concurrent syncs)
                val ids = toSync.map { it.id }
                locationDao.updateSyncState(ids, SyncState.SYNCING)
                
                // 4. Upload to server
                val dtos = toSync.map { LocationDto.fromModel(it.toModel()) }
                val response = locationApi.insertLocations(dtos)
                
                if (response.isSuccessful) {
                    val insertedRows = response.body()
                    val insertedCount = insertedRows?.size ?: 0
                    val sentCount = dtos.size
                    
                    if (insertedCount == sentCount) {
                        // 5. Mark as SYNCED (atomic transaction)
                        locationDao.markAsSyncedNew(ids)
                        
                        // 6. Update user's last_synced_at
                        updateUserLastSyncTime(userId)
                        
                        Log.d(TAG, "Successfully synced ${ids.size} locations")
                        
                        SyncResult.Success(
                            synced = ids.size,
                            timestamp = System.currentTimeMillis()
                        )
                    } else {
                        // 5. Partial failure: mark as FAILED
                        val errorMsg = "Partial insert: sent=$sentCount, inserted=$insertedCount"
                        Log.w(TAG, errorMsg)
                        
                        val (failed, abandoned) = handleSyncFailure(toSync, errorMsg)
                        
                        SyncResult.Failed(
                            reason = errorMsg,
                            failed = failed + abandoned,
                            errors = listOf(errorMsg)
                        )
                    }
                } else {
                    // 7. Handle HTTP failure
                    val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                    Log.w(TAG, "Sync failed: $errorMsg")
                    
                    val (failed, abandoned) = handleSyncFailure(toSync, errorMsg)
                    
                    SyncResult.Failed(
                        reason = errorMsg,
                        failed = failed + abandoned,
                        errors = listOf(errorMsg)
                    )
                }
            } catch (e: Exception) {
                // 8. Handle exception
                val errorMsg = e.message ?: "Unknown error"
                Log.e(TAG, "Sync exception: $errorMsg", e)
                
                // Get locations again (might have been updated)
                val toSync = locationDao.getSyncableLocations(
                    userId = userId,
                    maxAttempts = MAX_SYNC_ATTEMPTS,
                    batchSize = BATCH_SIZE
                )
                
                val (failed, abandoned) = handleSyncFailure(toSync, errorMsg)
                
                SyncResult.Failed(
                    reason = errorMsg,
                    failed = failed + abandoned,
                    errors = listOf(errorMsg)
                )
            }
        }

    /**
     * Force final sync that survives ViewModel cancellation.
     * 
     * CRITICAL: This method uses a dedicated sync scope that is NOT tied
     * to ViewModel lifecycle. It will complete even if:
     * - Activity finishes
     * - Navigation happens
     * - Configuration changes
     * - ViewModel is cleared
     * 
     * This is essential for checkout final sync to prevent data loss.
     * 
     * @return SyncResult with sync outcome
     */
    suspend fun forceFinalSync(): SyncResult = withContext(syncScope.coroutineContext) {
        Log.i(TAG, "forceFinalSync: starting (independent of UI lifecycle)")
        
        val result = try {
            syncUnsyncedLocations()
        } catch (e: Exception) {
            Log.e(TAG, "forceFinalSync: exception", e)
            SyncResult.Failed("Force sync exception: ${e.message}", 0)
        }
        
        Log.i(TAG, "forceFinalSync: completed - $result")
        result
    }

    /**
     * Handle sync failure: increment attempts, set FAILED or ABANDONED.
     * 
     * @return Pair of (failed count, abandoned count)
     */
    private suspend fun handleSyncFailure(
        locations: List<LocationEntity>,
        errorMessage: String
    ): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        var failedCount = 0
        var abandonedCount = 0
        
        locations.forEach { location ->
            val newAttempts = location.syncAttempts + 1
            val newState = if (newAttempts >= MAX_SYNC_ATTEMPTS) {
                SyncState.ABANDONED
            } else {
                SyncState.FAILED
            }
            
            locationDao.updateSyncFailure(
                id = location.id,
                state = newState,
                attempts = newAttempts,
                errorMessage = errorMessage.take(MAX_ERROR_MESSAGE_LENGTH),
                attemptedAt = now
            )
            
            if (newState == SyncState.ABANDONED) {
                abandonedCount++
                Log.w(TAG, "Location ${location.id} abandoned after $newAttempts attempts")
            } else {
                failedCount++
            }
        }
        
        return Pair(failedCount, abandonedCount)
    }

    /**
     * Update user's last_synced_at timestamp.
     */
    private suspend fun updateUserLastSyncTime(userId: String) {
        try {
            val currentTime = java.time.Instant.now().toString()
            userApi.updateUserLastSync(
                id = "eq.$userId",
                body = mapOf("last_synced_at" to currentTime)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update last_synced_at: ${e.message}")
        }
    }

    fun getLocationsByUser(userId: String): Flow<List<Location>> =
        locationDao.getLocationsByUser(userId).map { entities ->
            entities.map { it.toModel() }
        }

    fun getLocationsByUserAndDate(userId: String, date: String): Flow<List<Location>> =
        locationDao.getLocationsByUserAndDate(userId, date).map { entities ->
            entities.map { it.toModel() }
        }

    fun getLocationsSince(userId: String, sinceTimestamp: String): Flow<List<Location>> =
        locationDao.getLocationsSince(userId, sinceTimestamp).map { entities ->
            entities.map { it.toModel() }
        }

    suspend fun getUnsyncedCount(): Int = withContext(Dispatchers.IO) {
        val userId = sessionManager.userId ?: return@withContext 0
        locationDao.getUnsyncedCount(userId)
    }

    suspend fun getAbandonedLocations(): List<Location> = withContext(Dispatchers.IO) {
        val userId = sessionManager.userId ?: return@withContext emptyList()
        locationDao.getAbandonedLocations(userId).map { it.toModel() }
    }

    suspend fun retryAbandonedLocations(locationIds: List<String>) {
        withContext(Dispatchers.IO) {
            locationDao.retryAbandonedLocations(locationIds)
        }
    }

    suspend fun cleanupOldSyncedLocations(beforeTimestamp: String) {
        withContext(Dispatchers.IO) {
            val userId = sessionManager.userId ?: return@withContext
            locationDao.deleteOldSyncedLocations(beforeTimestamp, userId)
        }
    }

    suspend fun fetchRemoteLocations(userId: String): NetworkResult<List<Location>> =
        withContext(Dispatchers.IO) {
            try {
                val response = locationApi.getLocationsByUser(userId = "eq.$userId")
                if (response.isSuccessful && response.body() != null) {
                    val locations = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(locations)
                } else {
                    NetworkResult.Error("Failed to fetch locations")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun fetchRemoteLocationsByDate(
        userId: String,
        date: String
    ): NetworkResult<List<Location>> =
        withContext(Dispatchers.IO) {
            try {
                val response = locationApi.getLocationsByUserAndDate(
                    userId = "eq.$userId",
                    timestampFilter = "gte.${date}T00:00:00"
                )
                if (response.isSuccessful && response.body() != null) {
                    val locations = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(locations)
                } else {
                    NetworkResult.Error("Failed to fetch locations")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun getLocationsForSession(sessionId: String): List<Location> =
        withContext(Dispatchers.IO) {
            try {
                val response = locationApi.getLocationsBySession(
                    sessionId = "eq.$sessionId"
                )
                if (response.isSuccessful && response.body() != null) {
                    response.body()!!.map { it.toModel() }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                throw e
            }
        }
}
