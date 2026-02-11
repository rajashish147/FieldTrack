package com.fieldtrack.app.data.repository

import com.fieldtrack.app.data.local.dao.ExpenseDao
import com.fieldtrack.app.data.local.entity.ExpenseEntity
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

    suspend fun syncUnsyncedExpenses(): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val userId = sessionManager.userId
                    ?: return@withContext NetworkResult.Error("No user ID")
                val unsynced = expenseDao.getUnsyncedExpenses(userId)
                if (unsynced.isEmpty()) return@withContext NetworkResult.Success(Unit)

                var allSynced = true
                val syncedIds = mutableListOf<String>()

                for (entity in unsynced) {
                    val dto = ExpenseDto.fromModel(entity.toModel())
                    val response = expenseApi.insertExpense(dto)
                    if (response.isSuccessful) {
                        syncedIds.add(entity.id)
                    } else {
                        allSynced = false
                    }
                }

                if (syncedIds.isNotEmpty()) {
                    expenseDao.markAsSynced(syncedIds)
                }

                if (allSynced) NetworkResult.Success(Unit)
                else NetworkResult.Error("Some expenses failed to sync")
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Sync error")
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
                    val expenses = response.body()!!.map { it.toModel() }
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
}
