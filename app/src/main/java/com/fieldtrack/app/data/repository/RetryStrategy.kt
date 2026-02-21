package com.fieldtrack.app.data.repository

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Retry strategy with exponential backoff.
 * 
 * Implements exponential backoff with jitter to avoid thundering herd problem.
 * 
 * Retry delays:
 * - Attempt 1: 0ms (immediate)
 * - Attempt 2: 2000ms (2s)
 * - Attempt 3: 4000ms (4s)
 * - Attempt 4: 8000ms (8s)
 * - Attempt 5: 16000ms (16s)
 * 
 * After maxAttempts, item is marked as ABANDONED.
 */
object RetryStrategy {
    /**
     * Calculate delay for next retry attempt.
     * 
     * @param attempt Current attempt number (1-based)
     * @param baseDelayMs Base delay in milliseconds (default: 2000ms)
     * @param maxDelayMs Maximum delay in milliseconds (default: 30000ms)
     * @param jitterFactor Jitter factor (0.0 to 1.0, default: 0.1)
     * @return Delay in milliseconds
     */
    fun calculateDelay(
        attempt: Int,
        baseDelayMs: Long = 2000L,
        maxDelayMs: Long = 30000L,
        jitterFactor: Double = 0.1
    ): Long {
        if (attempt <= 1) return 0L // First attempt is immediate
        
        // Exponential backoff: baseDelay * 2^(attempt-2)
        val exponentialDelay = baseDelayMs * 2.0.pow(attempt - 2).toLong()
        
        // Cap at maxDelay
        val cappedDelay = min(exponentialDelay, maxDelayMs)
        
        // Add jitter to avoid thundering herd
        val jitter = (cappedDelay * jitterFactor * Math.random()).toLong()
        
        return cappedDelay + jitter
    }
    
    /**
     * Execute a block with retry logic.
     * 
     * @param maxAttempts Maximum number of attempts
     * @param baseDelayMs Base delay in milliseconds
     * @param maxDelayMs Maximum delay in milliseconds
     * @param block Block to execute
     * @return Result of the block
     * @throws Exception if all attempts fail
     */
    suspend fun <T> executeWithRetry(
        maxAttempts: Int = 5,
        baseDelayMs: Long = 2000L,
        maxDelayMs: Long = 30000L,
        block: suspend (attempt: Int) -> T
    ): T {
        var lastException: Exception? = null
        
        for (attempt in 1..maxAttempts) {
            try {
                return block(attempt)
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxAttempts) {
                    val delayMs = calculateDelay(attempt, baseDelayMs, maxDelayMs)
                    if (delayMs > 0) {
                        delay(delayMs)
                    }
                }
            }
        }
        
        throw lastException ?: Exception("All retry attempts failed")
    }
    
    /**
     * Check if an item should be retried based on attempt count.
     * 
     * @param attempts Current attempt count
     * @param maxAttempts Maximum attempts allowed
     * @return true if should retry, false if should abandon
     */
    fun shouldRetry(attempts: Int, maxAttempts: Int): Boolean {
        return attempts < maxAttempts
    }
    
    /**
     * Check if an item should be abandoned based on attempt count.
     * 
     * @param attempts Current attempt count
     * @param maxAttempts Maximum attempts allowed
     * @return true if should abandon, false otherwise
     */
    fun shouldAbandon(attempts: Int, maxAttempts: Int): Boolean {
        return attempts >= maxAttempts
    }
}
