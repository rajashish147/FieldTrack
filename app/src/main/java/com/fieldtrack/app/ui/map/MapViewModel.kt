package com.fieldtrack.app.ui.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.repository.LocationRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MapViewModel(private val locationRepository: LocationRepository) : ViewModel() {

    private val _locations = MutableLiveData<List<Location>>()
    val locations: LiveData<List<Location>> = _locations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadLocations(userId: String) {
        viewModelScope.launch {
            locationRepository.getLocationsByUser(userId).collectLatest { locations ->
                _locations.value = locations
            }
        }
    }

    fun loadLocationsByDate(userId: String, date: String) {
        viewModelScope.launch {
            locationRepository.getLocationsByUserAndDate(userId, date).collectLatest { locations ->
                _locations.value = locations
            }
        }
    }

    fun fetchRemoteLocations(userId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = locationRepository.fetchRemoteLocations(userId)) {
                is NetworkResult.Success -> _locations.value = result.data
                is NetworkResult.Error -> { /* handled in UI */ }
                is NetworkResult.Loading -> { }
            }
            _isLoading.value = false
        }
    }
}
