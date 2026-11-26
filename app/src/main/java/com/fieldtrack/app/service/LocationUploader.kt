package com.fieldtrack.app.service

import android.os.SystemClock
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.data.local.LocationQueueDao
import com.fieldtrack.app.core.data.local.LocationQueueEntity
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.EmployeeRequestOwner
import com.fieldtrack.app.core.network.dto.CreateLocationBatchRequest
import com.fieldtrack.app.core.network.dto.CreateLocationPointRequest
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationUploader @Inject constructor(
    private val queue: LocationQueueDao,
    private val api: ApiService,
    private val sessions: SessionManager,
) {
    private val uploadLock = Mutex()
    private val _closedSessions = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val closedSessions = _closedSessions.asSharedFlow()
    private var rateLimitedOwner: String? = null
    private var retryAtElapsedMillis = 0L

    /** True if there are no more retryable records for the current employee. */
    suspend fun drain(): Boolean = uploadLock.withLock {
        val auth = sessions.getSession()
        val owner = auth?.owner ?: return@withLock true
        if (auth.role != "EMPLOYEE") return@withLock true
        if (rateLimitedOwner == owner && SystemClock.elapsedRealtime() < retryAtElapsedMillis) return@withLock false

        repeat(8) {
            if (sessions.getSession()?.owner != owner) return@withLock true
            val rows = queue.getUnsynced(owner, limit = 100)
            if (rows.isEmpty()) return@withLock true

            val batch = rows.takeWhile { it.sessionId == rows.first().sessionId }
            if (!upload(owner, batch)) return@withLock false
        }
        queue.getUnsynced(owner, limit = 1).isEmpty()
    }

    suspend fun prune() {
        queue.clearSynced()
        queue.purgeBefore(ZonedDateTime.now(ZoneOffset.UTC).minusMonths(3).toInstant().toString())
    }

    private suspend fun upload(owner: String, batch: List<LocationQueueEntity>): Boolean {
        val sessionId = batch.first().sessionId
        val request = CreateLocationBatchRequest(
            sessionId,
            batch.map { row ->
                CreateLocationPointRequest(row.latitude, row.longitude, row.accuracy ?: 1000f, row.recordedAt)
            },
        )
        val response = try {
            api.recordLocationBatch(request, EmployeeRequestOwner(owner))
        } catch (_: IOException) {
            return false
        }
        if (response.isSuccessful) {
            val result = response.body()?.takeIf { it.success } ?: return false
            val discarded = result.discardedRecordedAt.orEmpty().toSet()
            val (rejected, acknowledged) = batch.partition { it.recordedAt in discarded }
            if (rejected.isNotEmpty()) queue.markRejected(owner, rejected.map { it.id }, "Outside shift")
            if (acknowledged.isNotEmpty()) queue.deleteAcknowledged(owner, acknowledged.map { it.id })
            if (result.sessionClosed) _closedSessions.emit(sessionId)
            return true
        }

        val code = response.errorBody()?.use { body ->
            runCatching { JsonParser.parseReader(body.charStream()).asJsonObject["code"]?.asString }.getOrNull()
        }
        if (response.code() == 429) {
            rateLimitedOwner = owner
            retryAtElapsedMillis = SystemClock.elapsedRealtime() + retryDelay(response.headers()["Retry-After"])
            return false
        }
        if (response.code() != 400) return false

        if (code == "LOCATION_SESSION_EXPIRED") {
            queue.markRejected(owner, batch.map { it.id }, "Shift expired or unavailable")
            _closedSessions.emit(sessionId)
            return true
        }
        if (code == "LOCATION_OUTSIDE_SESSION" || code == "VALIDATION_ERROR") {
            if (batch.size == 1) {
                queue.markRejected(owner, listOf(batch.single().id), "GPS point rejected")
                return true
            }
            // Older servers may reject a whole batch for one invalid point.
            val split = batch.size / 2
            return upload(owner, batch.take(split)) && upload(owner, batch.drop(split))
        }
        return false
    }

    private fun retryDelay(header: String?): Long {
        val seconds = header?.toLongOrNull() ?: header?.let {
            runCatching {
                ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() - Instant.now().epochSecond
            }.getOrNull()
        } ?: 10L
        return seconds.coerceIn(1, 3600) * 1000
    }
}
