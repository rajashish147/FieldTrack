package com.fieldtrack.app.ui.report

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.model.Report
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.StorageApi
import com.fieldtrack.app.data.repository.ReportRepository
import com.fieldtrack.app.util.ImageUtils
import com.fieldtrack.app.util.currentDateString
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.UUID

class ReportViewModel(
    private val reportRepository: ReportRepository,
    private val sessionManager: SessionManager,
    private val storageApi: StorageApi
) : ViewModel() {

    private val _reports = MutableLiveData<List<Report>>()
    val reports: LiveData<List<Report>> = _reports

    private val _submitResult = MutableLiveData<Boolean?>()
    val submitResult: LiveData<Boolean?> = _submitResult

    private val _hasReportToday = MutableLiveData<Boolean>()
    val hasReportToday: LiveData<Boolean> = _hasReportToday

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadReports() {
        val userId = sessionManager.userId ?: return
        viewModelScope.launch {
            reportRepository.getReportsByUser(userId).collectLatest { reports ->
                _reports.value = reports
            }
        }
    }

    fun checkTodayReport() {
        val userId = sessionManager.userId ?: return
        viewModelScope.launch {
            val exists = reportRepository.hasReportForToday(userId, currentDateString())
            _hasReportToday.value = exists
        }
    }

    fun submitReport(context: Context, summary: String, imageUri: Uri? = null) {
        val userId = sessionManager.userId ?: return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                var imageUrl: String? = null

                if (imageUri != null) {
                    imageUrl = uploadImage(context, userId, imageUri)
                }

                val report = Report(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    date = currentDateString(),
                    summary = summary,
                    optionalImageUrl = imageUrl
                )

                reportRepository.saveReportLocally(report)
                reportRepository.syncUnsyncedReports()
                _hasReportToday.value = true
                _submitResult.value = true
            } catch (e: Exception) {
                _error.postValue(e.message ?: "Failed to submit report")
                _submitResult.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun uploadImage(context: Context, userId: String, uri: Uri): String? {
        val compressedFile = ImageUtils.compressImage(context, uri) ?: return null

        return try {
            val fileName = "${userId}/${currentDateString()}_${System.currentTimeMillis()}.jpg"
            val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())

            val response = storageApi.uploadFile("reports", fileName, "image/jpeg", requestBody)
            if (response.isSuccessful) {
                "reports/$fileName"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            compressedFile.delete()
        }
    }

    fun syncReports() {
        _isLoading.value = true
        viewModelScope.launch {
            reportRepository.syncUnsyncedReports()
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSubmitResult() {
        _submitResult.value = null
    }

    fun getAuthToken(): String? {
        return sessionManager.accessToken
    }
}
