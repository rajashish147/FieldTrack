package com.fieldtrack.app.ui.expense

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.Expense
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.currentIso8601
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class ExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _expenses = MutableLiveData<List<Expense>>()
    val expenses: LiveData<List<Expense>> = _expenses

    private val _submitResult = MutableLiveData<Boolean>()
    val submitResult: LiveData<Boolean> = _submitResult

    fun loadExpenses() {
        val userId = sessionManager.userId ?: return
        viewModelScope.launch {
            expenseRepository.getExpensesByUser(userId).collectLatest { expenses ->
                _expenses.value = expenses
            }
        }
    }

    fun submitExpense(amount: Double, category: String, billImageUrl: String) {
        val userId = sessionManager.userId ?: return
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            userId = userId,
            amount = amount,
            category = category,
            billImageUrl = billImageUrl,
            status = Constants.EXPENSE_STATUS_PENDING,
            timestamp = currentIso8601()
        )
        viewModelScope.launch {
            expenseRepository.saveExpenseLocally(expense)
            expenseRepository.syncUnsyncedExpenses()
            _submitResult.value = true
        }
    }

    fun syncExpenses() {
        viewModelScope.launch {
            expenseRepository.syncUnsyncedExpenses()
        }
    }
}
