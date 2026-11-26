package com.fieldtrack.app.features.employee.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.core.data.local.LocationQueueDao
import com.fieldtrack.app.core.network.dto.AttendanceSession
import com.fieldtrack.app.core.network.dto.DashboardSummary
import com.fieldtrack.app.core.network.dto.FieldReport
import com.fieldtrack.app.core.network.dto.FieldSetup
import com.fieldtrack.app.core.network.dto.FieldSite
import com.fieldtrack.app.core.network.dto.ReachedSite
import com.fieldtrack.app.core.network.dto.SelfieChallenge
import com.fieldtrack.app.service.LocationForegroundService
import com.fieldtrack.app.service.LocationSyncWorker
import com.fieldtrack.app.service.LocationUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val shiftVerified: Boolean = false,
    val summary: DashboardSummary? = null,
    val activeSession: AttendanceSession? = null,
    val trackingActive: Boolean = false,
    val pendingLocations: Int = 0,
    val isCheckingIn: Boolean = false,
    val isCheckingOut: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val cameraChallenge: SelfieChallenge? = null,
    val fieldSetup: FieldSetup? = null,
    val fieldReport: FieldReport? = null,
    val fieldPeriod: String = "month",
    val fieldLoading: Boolean = true,
    val fieldError: String? = null,
    val confirmingSiteId: String? = null,
    val arrivalReceipts: Map<String, ReachedSite> = emptyMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: HomeRepository,
    private val queue: LocationQueueDao,
    private val uploader: LocationUploader,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var cameraOwner: String? = null
    private var reportJob: Job? = null
    private var summaryJob: Job? = null

    init {
        loadSummary()
        loadFieldOverview()
        viewModelScope.launch {
            LocationForegroundService.activeShift.collectLatest { tracking ->
                _state.update {
                    it.copy(trackingActive = tracking != null, pendingLocations = 0)
                }
                if (tracking != null) {
                    queue.observePending(tracking.owner, tracking.sessionId).collect { pending ->
                        _state.update { it.copy(pendingLocations = pending) }
                    }
                }
            }
        }
        viewModelScope.launch {
            uploader.closedSessions.collect { closed ->
                if (_state.value.activeSession?.id == closed) {
                    _state.update { it.copy(activeSession = null, arrivalReceipts = emptyMap(), isCheckingOut = false) }
                    loadSummary()
                    loadFieldOverview()
                }
            }
        }
    }

    fun loadFieldOverview(period: String = _state.value.fieldPeriod) {
        reportJob?.cancel()
        _state.update { it.copy(fieldLoading = true, fieldPeriod = period, fieldError = null, fieldReport = null) }
        reportJob = viewModelScope.launch {
            val setup = repo.fieldSetup().getOrElse { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(fieldLoading = false, fieldError = error.message) }
                return@launch
            }
            _state.update { it.copy(fieldSetup = setup) }
            if (setup == null) {
                _state.update { it.copy(fieldLoading = false) }
                return@launch
            }
            repo.fieldReport(period, setup.settings.timezone).onSuccess { report ->
                _state.update { it.copy(fieldLoading = false, fieldReport = report) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(fieldLoading = false, fieldError = error.message) }
            }
        }
    }

    fun loadSummary() {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            try {
                val owner = repo.employeeOwner()
                val shift = repo.activeShift(owner).getOrThrow()
                _state.update {
                    it.copy(
                        isLoading = false,
                        shiftVerified = true,
                        activeSession = shift,
                        arrivalReceipts = if (it.activeSession?.id == shift?.id) it.arrivalReceipts else emptyMap(),
                    )
                }
                if (shift != null) LocationForegroundService.start(context, owner, shift)
                else LocationForegroundService.activeShift.value?.takeIf { it.owner == owner }?.let {
                    LocationForegroundService.stop(context, it)
                }
                repo.getDashboardSummary().onSuccess { summary -> _state.update { it.copy(summary = summary) } }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun prepareCheckIn() {
        if (_state.value.isCheckingIn) return
        viewModelScope.launch {
            _state.update { it.copy(isCheckingIn = true, error = null) }
            try {
                val owner = repo.employeeOwner()
                val challenge = repo.prepareCamera(owner).getOrThrow()
                cameraOwner = owner
                _state.update { it.copy(isCheckingIn = false, cameraChallenge = challenge) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(isCheckingIn = false, error = error.message) }
            }
        }
    }

    fun cancelCamera() {
        if (!_state.value.isCheckingIn) {
            cameraOwner = null
            _state.update { it.copy(cameraChallenge = null) }
        }
    }

    fun checkIn(photo: File, capturedAt: String) {
        val challenge = _state.value.cameraChallenge
        val owner = cameraOwner
        if (_state.value.isCheckingIn || challenge == null || owner == null) {
            photo.delete()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCheckingIn = true, error = null) }
            try {
                val session = repo.checkIn(owner, challenge, photo, capturedAt).getOrThrow()
                summaryJob?.cancel()
                LocationForegroundService.start(context, owner, session)
                _state.update {
                    it.copy(
                        isCheckingIn = false,
                        cameraChallenge = null,
                        actionMessage = "Checked in successfully",
                        activeSession = session,
                        arrivalReceipts = emptyMap(),
                    )
                }
                loadFieldOverview()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(isCheckingIn = false, cameraChallenge = null, error = error.message) }
                loadSummary()
            } finally {
                photo.delete()
                cameraOwner = null
            }
        }
    }

    fun checkOut() {
        val active = _state.value.activeSession ?: return
        if (_state.value.isCheckingOut || _state.value.confirmingSiteId != null) return
        viewModelScope.launch {
            _state.update { it.copy(isCheckingOut = true, error = null) }
            try {
                val owner = repo.employeeOwner()
                repo.checkOut(owner).getOrThrow()
                summaryJob?.cancel()
                val activeTracking = LocationForegroundService.activeShift.value
                if (activeTracking?.owner == owner && activeTracking.sessionId == active.id) {
                    LocationForegroundService.stop(context, activeTracking)
                }
                LocationSyncWorker.flush(context)
                _state.update {
                    it.copy(
                        isCheckingOut = false,
                        actionMessage = "Checked out successfully",
                        activeSession = null,
                        arrivalReceipts = emptyMap(),
                    )
                }
                loadSummary()
                loadFieldOverview()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(isCheckingOut = false, error = error.message) }
                loadSummary()
            }
        }
    }

    fun confirmArrival(site: FieldSite) {
        if (_state.value.activeSession == null || _state.value.confirmingSiteId != null || _state.value.isCheckingOut) return
        viewModelScope.launch {
            _state.update { it.copy(confirmingSiteId = site.id, error = null) }
            try {
                val owner = repo.employeeOwner()
                withTimeoutOrNull(2_000) { uploader.drain() }
                val receipt = repo.confirmArrival(owner, site.id).getOrThrow()
                _state.update {
                    it.copy(
                        confirmingSiteId = null,
                        arrivalReceipts = it.arrivalReceipts + (site.id to receipt),
                        actionMessage = "GPS verified arrival at ${site.name}",
                    )
                }
                loadFieldOverview()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(confirmingSiteId = null, error = error.message) }
            }
        }
    }

    fun clearMessage() { _state.update { it.copy(actionMessage = null) } }
    fun clearError() { _state.update { it.copy(error = null) } }
}
