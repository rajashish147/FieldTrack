package com.fieldtrack.app.ui.expense

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.model.Expense
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.StorageApi
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.ImageUtils
import com.fieldtrack.app.util.currentIso8601
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.UUID

class ExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
    private val storageApi: StorageApi
) : ViewModel() {

    private val _expenses = MutableLiveData<List<Expense>>()
    val expenses: LiveData<List<Expense>> = _expenses

    private val _submitResult = MutableLiveData<Boolean?>()
    val submitResult: LiveData<Boolean?> = _submitResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadExpenses() {
        val userId = sessionManager.userId ?: return
        viewModelScope.launch {
            expenseRepository.getExpensesByUser(userId).collectLatest { expenses ->
                _expenses.value = expenses
            }
        }
    }

    fun submitExpense(context: Context, amount: Double, category: String, billImageUri: Uri?) {
        val userId = sessionManager.userId ?: return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                var billImageUrl = ""

                if (billImageUri != null) {
                    billImageUrl = uploadBillImage(context, userId, billImageUri) ?: ""
                }

                val expense = Expense(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    amount = amount,
                    category = category,
                    billImageUrl = billImageUrl,
                    status = Constants.EXPENSE_STATUS_PENDING,
                    timestamp = currentIso8601()
                )

                expenseRepository.saveExpenseLocally(expense)
                expenseRepository.syncUnsyncedExpenses()
                _submitResult.value = true
            } catch (e: Exception) {
                _error.postValue(e.message ?: "Failed to submit expense")
                _submitResult.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun uploadBillImage(context: Context, userId: String, uri: Uri): String? {
        val compressedFile = ImageUtils.compressImage(context, uri) ?: return null

        return try {
            val fileName = "expenses/${userId}/${System.currentTimeMillis()}.jpg"
            val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", compressedFile.name, requestBody)

            val response = storageApi.uploadFile("expenses", fileName, part)
            if (response.isSuccessful) {
                "${BuildConfig.SUPABASE_URL}storage/v1/object/public/expenses/$fileName"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            compressedFile.delete()
        }
    }

    fun syncExpenses() {
        _isLoading.value = true
        viewModelScope.launch {
            expenseRepository.syncUnsyncedExpenses()
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSubmitResult() {
        _submitResult.value = null
    }
}
