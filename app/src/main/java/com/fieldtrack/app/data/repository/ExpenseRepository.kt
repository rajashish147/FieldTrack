package com.fieldtrack.app.data.repository

import android.util.Log
import com.fieldtrack.app.data.local.dao.ExpenseDao
import com.fieldtrack.app.data.local.entity.ExpenseEntity
import com.fieldtrack.app.data.local.entity.SyncState
import com.fieldtrack.app.data.model.Expense
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.ExpenseApi
import com.fieldtrack.app.data.remote.dto.ExpenseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val expenseApi: ExpenseApi,
    private val sessionManager: SessionManager
) {
    companion object {
        const val TAG = "ExpenseRepository"
        const val MAX_SYNC_ATTEMPTS = 5
        const val SYNC_TIMEOUT_MS = 5 * 60 * 1000L  // 5 minutes
        const val BATCH_SIZE = 50
        private const val MAX_ERROR_MESSAGE_LENGTH = 255
        private const val MAX_ERROR_LIST_SIZE = 10
    }

    suspend fun saveExpenseLocally(expense: Expense) {
        withContext(Dispatchers.IO) {
            expenseDao.insert(ExpenseEntity.fromModel(expense))
        }
    }

    fun getExpensesByUser(userId: String): Flow<List<Expense>> =
        expenseDao.getExpensesByUser(userId).map { entities ->
            entities.map { it.toModel() }
        }

    /**
     * Pull the latest expense data from the server and upsert into Room.
     * This ensures status changes (e.g. admin approval) are reflected locally.
     */
    suspend fun refreshExpensesFromServer(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val response = expenseApi.getExpensesByUser(userId = "eq.$userId")
                if (response.isSuccessful && response.body() != null) {
                    response.body()!!.forEach { dto ->
                        expenseDao.insert(ExpenseEntity.fromModel(dto.toModel(), synced = true))
                    }
                }
            } catch (_: Exception) { /* silent — local cache still works */ }
        }
    }

    fun getExpensesByStatus(userId: String, status: String): Flow<List<Expense>> =
        expenseDao.getExpensesByStatus(userId, status).map { entities ->
            entities.map { it.toModel() }
        }

    /**
     * Hardened sync with deterministic state transitions.
     * Implements: LOCAL_ONLY → SYNCING → SYNCED | FAILED | ABANDONED
     */
    suspend fun syncUnsyncedExpenses(): SyncResult =
        withContext(Dispatchers.IO) {
            val userId = sessionManager.userId 
                ?: return@withContext SyncResult.Failed("No user ID", 0)
            
            try {
                // 1. Reset stale SYNCING states (crash recovery)
                val timeoutThreshold = System.currentTimeMillis() - SYNC_TIMEOUT_MS
                expenseDao.resetStaleSyncingState(timeoutThreshold)
                
                // 2. Get syncable expenses
                val toSync = expenseDao.getSyncableExpenses(
                    userId = userId,
                    maxAttempts = MAX_SYNC_ATTEMPTS,
                    batchSize = BATCH_SIZE
                )
                
                if (toSync.isEmpty()) {
                    Log.d(TAG, "No expenses to sync")
                    return@withContext SyncResult.Success(0, System.currentTimeMillis())
                }
                
                Log.d(TAG, "Syncing ${toSync.size} expenses")
                
                // 3. Mark as SYNCING
                val ids = toSync.map { it.id }
                expenseDao.updateSyncState(ids, SyncState.SYNCING)
                
                // 4. Sync individually to handle partial failures
                var syncedCount = 0
                var failedCount = 0
                var abandonedCount = 0
                val errors = mutableListOf<String>()
                
                for (entity in toSync) {
                    try {
                        val model = entity.toModel()
                        val body = mutableMapOf<String, Any>(
                            "id" to model.id,
                            "user_id" to model.userId,
                            "amount" to model.amount,
                            "category" to model.category,
                            "status" to model.status,
                            "timestamp" to model.timestamp
                        )
                        if (model.billImageUrl.isNotBlank()) {
                            body["bill_image_url"] = model.billImageUrl
                        }
                        val response = expenseApi.insertExpense(body)
                        
                        if (response.isSuccessful) {
                            expenseDao.markAsSyncedNew(listOf(entity.id))
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
                
                Log.d(TAG, "Expense sync complete: $syncedCount synced, $failedCount failed, $abandonedCount abandoned")
                
                // 5. Return appropriate result
                when {
                    syncedCount == toSync.size -> SyncResult.Success(syncedCount, System.currentTimeMillis())
                    syncedCount > 0 -> SyncResult.Partial(syncedCount, failedCount, abandonedCount, errors.take(MAX_ERROR_LIST_SIZE))
                    else -> SyncResult.Failed("All expenses failed to sync", failedCount + abandonedCount, errors.take(MAX_ERROR_LIST_SIZE))
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Log.e(TAG, "Sync exception: $errorMsg", e)
                SyncResult.Failed(errorMsg, 0)
            }
        }
    
    /**
     * Handle sync failure for a single expense.
     * 
     * @return Pair of (failed count, abandoned count)
     */
    private suspend fun handleSyncFailure(
        expense: ExpenseEntity,
        errorMessage: String
    ): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val newAttempts = expense.syncAttempts + 1
        val newState = if (newAttempts >= MAX_SYNC_ATTEMPTS) {
            SyncState.ABANDONED
        } else {
            SyncState.FAILED
        }
        
        expenseDao.updateSyncFailure(
            id = expense.id,
            state = newState,
            attempts = newAttempts,
            errorMessage = errorMessage.take(MAX_ERROR_MESSAGE_LENGTH),
            attemptedAt = now
        )
        
        return if (newState == SyncState.ABANDONED) {
            Log.w(TAG, "Expense ${expense.id} abandoned after $newAttempts attempts")
            Pair(0, 1)
        } else {
            Pair(1, 0)
        }
    }

    suspend fun fetchRemoteExpenses(userId: String): NetworkResult<List<Expense>> =
        withContext(Dispatchers.IO) {
            try {
                val response = expenseApi.getExpensesByUser(userId = "eq.$userId")
                if (response.isSuccessful && response.body() != null) {
                    val expenses = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(expenses)
                } else {
                    NetworkResult.Error("Failed to fetch expenses")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun fetchAllExpenses(): NetworkResult<List<Expense>> =
        withContext(Dispatchers.IO) {
            try {
                val response = expenseApi.getAllExpenses()
                if (response.isSuccessful && response.body() != null) {
                    val expenses = response.body()!!.map { dto ->
                        if (com.fieldtrack.app.BuildConfig.DEBUG) {
                            Log.d("ExpenseRelation", "Expense ${dto.id} → user=${dto.user?.name}")
                        }
                        dto.toModel()
                    }
                    NetworkResult.Success(expenses)
                } else {
                    NetworkResult.Error("Failed to fetch expenses")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun updateExpenseStatus(
        expenseId: String,
        status: String
    ): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = expenseApi.updateExpenseStatus(
                    id = "eq.$expenseId",
                    body = mapOf("status" to status)
                )
                if (response.isSuccessful) {
                    NetworkResult.Success(Unit)
                } else {
                    NetworkResult.Error("Failed to update expense status")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }
    
    suspend fun getAbandonedExpenses(): List<Expense> = withContext(Dispatchers.IO) {
        val userId = sessionManager.userId ?: return@withContext emptyList()
        expenseDao.getAbandonedExpenses(userId).map { it.toModel() }
    }
    
    suspend fun retryAbandonedExpenses(expenseIds: List<String>) {
        withContext(Dispatchers.IO) {
            expenseDao.retryAbandonedExpenses(expenseIds)
        }
    }
}
