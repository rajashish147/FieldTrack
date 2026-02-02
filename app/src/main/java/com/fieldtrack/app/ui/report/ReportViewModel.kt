package com.fieldtrack.app.ui.report

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.Report
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.ReportRepository
import com.fieldtrack.app.util.currentDateString
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class ReportViewModel(
    private val reportRepository: ReportRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _reports = MutableLiveData<List<Report>>()
    val reports: LiveData<List<Report>> = _reports

    private val _submitResult = MutableLiveData<Boolean>()
    val submitResult: LiveData<Boolean> = _submitResult

    fun loadReports() {
        val userId = sessionManager.userId ?: return
        viewModelScope.launch {
            reportRepository.getReportsByUser(userId).collectLatest { reports ->
                _reports.value = reports
            }
        }
    }

    fun submitReport(summary: String, imageUrl: String? = null) {
        val userId = sessionManager.userId ?: return
        val report = Report(
            id = UUID.randomUUID().toString(),
            userId = userId,
            date = currentDateString(),
            summary = summary,
            optionalImageUrl = imageUrl
        )
        viewModelScope.launch {
            reportRepository.saveReportLocally(report)
            reportRepository.syncUnsyncedReports()
            _submitResult.value = true
        }
    }

    fun syncReports() {
        viewModelScope.launch {
            reportRepository.syncUnsyncedReports()
        }
    }
}
