package com.fieldtrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtrack.app.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<LocationEntity>)

    @Query("SELECT * FROM locations WHERE userId = :userId ORDER BY timestamp DESC")
    fun getLocationsByUser(userId: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE userId = :userId AND date(timestamp) = :date ORDER BY timestamp ASC")
    fun getLocationsByUserAndDate(userId: String, date: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE userId = :userId AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getLocationsSince(userId: String, sinceTimestamp: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedLocations(userId: String): List<LocationEntity>

    @Query("UPDATE locations SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Query("DELETE FROM locations WHERE timestamp < :beforeTimestamp AND isSynced = 1 AND userId = :userId")
    suspend fun deleteOldSyncedLocations(beforeTimestamp: String, userId: String)

    @Query("SELECT COUNT(*) FROM locations WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedCount(userId: String): Int

    @Query("DELETE FROM locations WHERE userId = :userId")
    suspend fun deleteAll(userId: String)
}
