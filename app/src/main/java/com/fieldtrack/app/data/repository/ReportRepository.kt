package com.fieldtrack.app.data.repository

import com.fieldtrack.app.data.local.dao.ReportDao
import com.fieldtrack.app.data.local.entity.ReportEntity
import com.fieldtrack.app.data.model.Report
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.api.ReportApi
import com.fieldtrack.app.data.remote.dto.ReportDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReportRepository(
    private val reportDao: ReportDao,
    private val reportApi: ReportApi
) {

    suspend fun saveReportLocally(report: Report) {
        withContext(Dispatchers.IO) {
            reportDao.insert(ReportEntity.fromModel(report))
        }
    }

    fun getReportsByUser(userId: String): Flow<List<Report>> =
        reportDao.getReportsByUser(userId).map { entities ->
            entities.map { it.toModel() }
        }

    suspend fun syncUnsyncedReports(): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val unsynced = reportDao.getUnsyncedReports()
                if (unsynced.isEmpty()) return@withContext NetworkResult.Success(Unit)

                var allSynced = true
                val syncedIds = mutableListOf<String>()

                for (entity in unsynced) {
                    val dto = ReportDto.fromModel(entity.toModel())
                    val response = reportApi.insertReport(dto)
                    if (response.isSuccessful) {
                        syncedIds.add(entity.id)
                    } else {
                        allSynced = false
                    }
                }

                if (syncedIds.isNotEmpty()) {
                    reportDao.markAsSynced(syncedIds)
                }

                if (allSynced) NetworkResult.Success(Unit)
                else NetworkResult.Error("Some reports failed to sync")
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Sync error")
            }
        }

    suspend fun fetchRemoteReports(userId: String): NetworkResult<List<Report>> =
        withContext(Dispatchers.IO) {
            try {
                val response = reportApi.getReportsByUser(userId = "eq.$userId")
                if (response.isSuccessful && response.body() != null) {
                    val reports = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(reports)
                } else {
                    NetworkResult.Error("Failed to fetch reports")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun fetchAllReports(): NetworkResult<List<Report>> =
        withContext(Dispatchers.IO) {
            try {
                val response = reportApi.getAllReports()
                if (response.isSuccessful && response.body() != null) {
                    val reports = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(reports)
                } else {
                    NetworkResult.Error("Failed to fetch reports")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error")
            }
        }
}
