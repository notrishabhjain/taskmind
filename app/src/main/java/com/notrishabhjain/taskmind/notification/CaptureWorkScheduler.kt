package com.notrishabhjain.taskmind.notification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.notrishabhjain.taskmind.data.worker.CaptureProcessingWorker
import java.util.concurrent.TimeUnit

/**
 * Coalesced WorkManager scheduling for the capture drain. All entry points
 * (listener, app resume, boot receiver) funnel into the same unique work so
 * bursts never create unbounded WorkRequests.
 */
class CaptureWorkScheduler(private val workManager: WorkManager) {

    fun scheduleDrain() {
        // REPLACE (not KEEP): a persisted, backed-off drain chain from an
        // earlier build/failure must never silently block fresh enqueues —
        // otherwise notifications stay stuck in CAPTURED indefinitely.
        workManager.enqueueUniqueWork(
            DRAIN_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            drainRequest()
        )
    }

    fun scheduleMaintenance() {
        workManager.enqueueUniqueWork(
            MAINTENANCE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CaptureProcessingWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CaptureRetryPolicy.WORKER_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    private fun drainRequest() =
        OneTimeWorkRequestBuilder<CaptureProcessingWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CaptureRetryPolicy.WORKER_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    companion object {
        const val DRAIN_WORK_NAME = "capture-processing-drain"
        const val MAINTENANCE_WORK_NAME = "capture-processing-maintenance"
    }
}
