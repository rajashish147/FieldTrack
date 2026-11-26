package com.fieldtrack.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.data.local.PendingExpenseDao
import com.fieldtrack.app.core.data.local.PendingExpenseEntity
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.EmployeeRequestOwner
import com.fieldtrack.app.core.network.dto.CreateExpenseRequest
import com.google.gson.JsonParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ExpenseUpload { CONFIRMED, RETRY, REJECTED }

@Singleton
class ExpenseUploader @Inject constructor(
    private val queue: PendingExpenseDao,
    private val api: ApiService,
) {
    suspend fun upload(expense: PendingExpenseEntity): ExpenseUpload {
        val owner = checkNotNull(expense.owner)
        val request = CreateExpenseRequest(
            amount = expense.amount,
            description = expense.description,
            receiptUrl = expense.receiptUrl,
            idempotencyKey = checkNotNull(expense.idempotencyKey),
        )
        val response = try {
            api.createExpense(request, EmployeeRequestOwner(owner))
        } catch (_: IOException) {
            return ExpenseUpload.RETRY
        }
        if (response.isSuccessful) {
            queue.delete(expense.id, owner)
            return ExpenseUpload.CONFIRMED
        }
        val error = response.errorBody()?.use { body ->
            runCatching { JsonParser.parseReader(body.charStream()).asJsonObject["error"]?.asString }.getOrNull()
        }
        // Compatibility for servers that acknowledged an existing key as a 400 error.
        if (response.code() == 400 && error == "Duplicate expense submission (idempotency key already used)") {
            queue.delete(expense.id, owner)
            return ExpenseUpload.CONFIRMED
        }
        if (response.code() !in listOf(400, 404, 409, 422)) return ExpenseUpload.RETRY
        queue.markRejected(expense.id, owner, error ?: "Expense rejected (${response.code()})")
        return ExpenseUpload.REJECTED
    }
}

@HiltWorker
class ExpenseRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingExpenses: PendingExpenseDao,
    private val sessions: SessionManager,
    private val uploader: ExpenseUploader,
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val WORK_NAME = "ft_expense_retry"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ExpenseRetryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        pendingExpenses.clearConfirmed()
        val auth = sessions.getSession()
        val owner = auth?.owner ?: return Result.success()
        if (auth.role != "EMPLOYEE") return Result.success()

        repeat(4) {
            val pending = pendingExpenses.getUnconfirmed(owner)
            if (pending.isEmpty()) return Result.success()
            for (expense in pending) {
                if (sessions.getSession()?.owner != owner) return Result.success()
                if (uploader.upload(expense) == ExpenseUpload.RETRY) return Result.retry()
            }
        }
        return if (pendingExpenses.getUnconfirmed(owner).isEmpty()) Result.success() else Result.retry()
    }
}
