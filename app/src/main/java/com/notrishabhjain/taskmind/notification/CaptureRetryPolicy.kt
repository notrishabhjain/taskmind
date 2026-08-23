package com.notrishabhjain.taskmind.notification

/**
 * Deterministic retry policy for notification capture processing.
 *
 * retryCount semantics: the number of RETRY transitions that have already
 * happened for a capture. The first processing attempt is not a retry.
 *
 * A capture is given up when the NEXT scheduled retry would exceed
 * [maxRetries]; at that point the worker transitions it to FAILED instead.
 */
data class CaptureRetryPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES
) {

    fun isRetryExhausted(retryCountAfterIncrement: Int): Boolean =
        retryCountAfterIncrement > maxRetries

    companion object {
        const val DEFAULT_MAX_RETRIES = 3

        /** Backoff used by WorkManager between attempts; WM owns real timing. */
        const val WORKER_BACKOFF_MILLIS = 30_000L
    }
}
