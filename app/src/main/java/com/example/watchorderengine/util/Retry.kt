package com.example.watchorderengine.util

import android.util.Log
import kotlinx.coroutines.delay

private const val TAG = "RetryUtil"

/**
 * Generic retry helper with exponential backoff.
 */
suspend fun <T> retry(
    times: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 4000,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            Log.w(TAG, "Retryable failure: ${e.message}. Retrying in ${currentDelay}ms...")
        }
        delay(currentDelay)
        currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
    }
    return block()
}
