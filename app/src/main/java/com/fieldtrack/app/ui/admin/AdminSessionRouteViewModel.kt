package com.fieldtrack.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.repository.LocationRepository
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AdminSessionRouteViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _routePoints = MutableLiveData<List<Location>>()
    val routePoints: LiveData<List<Location>> = _routePoints

    private val _totalDistanceKm = MutableLiveData<Double>()
    val totalDistanceKm: LiveData<Double> = _totalDistanceKm

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadSessionRoute(sessionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val locations = locationRepository.getLocationsForSession(sessionId)
                _routePoints.value = locations
                
                if (locations.size >= 2) {
                    val distance = calculateTotalDistance(locations)
                    _totalDistanceKm.value = distance
                } else {
                    _totalDistanceKm.value = 0.0
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load route"
                _routePoints.value = emptyList()
                _totalDistanceKm.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun calculateTotalDistance(locations: List<Location>): Double {
        var totalMeters = 0.0
        
        for (i in 0 until locations.size - 1) {
            val start = locations[i]
            val end = locations[i + 1]
            totalMeters += haversineDistance(
                start.latitude, start.longitude,
                end.latitude, end.longitude
            )
        }
        
        val kilometers = totalMeters / 1000.0
        return String.format("%.2f", kilometers).toDouble()
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6371000.0
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadiusMeters * c
    }

    fun clearError() {
        _error.value = null
    }
}
