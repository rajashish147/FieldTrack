package com.fieldtrack.app.data.repository

import com.fieldtrack.app.data.local.dao.LocationDao
import com.fieldtrack.app.data.local.entity.LocationEntity
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.api.LocationApi
import com.fieldtrack.app.data.remote.dto.LocationDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocationRepository(
    private val locationDao: LocationDao,
    private val locationApi: LocationApi
) {

    suspend fun saveLocationLocally(location: Location) {
        withContext(Dispatchers.IO) {
            locationDao.insert(LocationEntity.fromModel(location))
        }
    }

    fun getLocationsByUser(userId: String): Flow<List<Location>> =
        locationDao.getLocationsByUser(userId).map { entities ->
            entities.map { it.toModel() }
        }

    fun getLocationsByUserAndDate(userId: String, date: String): Flow<List<Location>> =
        locationDao.getLocationsByUserAndDate(userId, date).map { entities ->
            entities.map { it.toModel() }
        }

    suspend fun syncUnsyncedLocations(): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val unsynced = locationDao.getUnsyncedLocations()
                if (unsynced.isEmpty()) return@withContext NetworkResult.Success(Unit)

                val dtos = unsynced.map { LocationDto.fromModel(it.toModel()) }
                val response = locationApi.insertLocations(dtos)

                if (response.isSuccessful) {
                    locationDao.markAsSynced(unsynced.map { it.id })
                    NetworkResult.Success(Unit)
                } else {
                    NetworkResult.Error("Sync failed: ${response.code()}")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Sync error")
            }
        }

    suspend fun fetchRemoteLocations(userId: String): NetworkResult<List<Location>> =
        withContext(Dispatchers.IO) {
            try {
                val response = locationApi.getLocationsByUser(userId = "eq.$userId")
                if (response.isSuccessful && response.body() != null) {
                    val locations = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(locations)
                } else {
                    NetworkResult.Error("Failed to fetch locations")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun fetchRemoteLocationsByDate(
        userId: String,
        date: String
    ): NetworkResult<List<Location>> =
        withContext(Dispatchers.IO) {
            try {
                val response = locationApi.getLocationsByUserAndDate(
                    userId = "eq.$userId",
                    timestampFilter = "gte.${date}T00:00:00"
                )
                if (response.isSuccessful && response.body() != null) {
                    val locations = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(locations)
                } else {
                    NetworkResult.Error("Failed to fetch locations")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun getUnsyncedCount(): Int = withContext(Dispatchers.IO) {
        locationDao.getUnsyncedCount()
    }

    suspend fun cleanupOldSyncedLocations(beforeTimestamp: String) {
        withContext(Dispatchers.IO) {
            locationDao.deleteOldSyncedLocations(beforeTimestamp)
        }
    }
}
