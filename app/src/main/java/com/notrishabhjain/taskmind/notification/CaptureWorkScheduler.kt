package com.notrishabhjain.taskmind.notification

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.notrishabhjain.taskmind.data.worker.CaptureProcessingWorker
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Coalesced WorkManager scheduling for the capture drain. All entry points
 * (listener, app resume, boot receiver) funnel into the same unique work so
 * bursts never create unbounded WorkRequests.
 *
 * Every scheduling attempt is durably recorded in the Activity Log (metadata
 * only: trigger source and enqueue outcome) so on-device diagnostics work
 * without adb.
 */
class CaptureWorkScheduler(
    private val workManager: WorkManager,
    private val activityLogRepository: ActivityLogRepository,
    private val timeProvider: TimeProvider
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun scheduleDrain(trigger: String) {
        Log.i(TAG, "scheduleDrain entered source=$trigger")
        record("Capture drain scheduled", "source=$trigger outcome=attempted")
        try {
            // REPLACE (not KEEP): a persisted, backed-off drain chain from an
            // earlier build/failure must never silently block fresh enqueues —
            // otherwise notifications stay stuck in CAPTURED indefinitely.
            val operation = workManager.enqueueUniqueWork(
                DRAIN_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                drainRequest()
            )
            Log.i(TAG, "enqueueUniqueWork invoked name=$DRAIN_WORK_NAME")
            operation.result.addListener(
                {
                    runCatching { operation.result.get() }
                        .onSuccess {
                            Log.i(TAG, "enqueue completed successfully")
                            record("Capture drain scheduled", "source=$trigger outcome=enqueued")
                            logDrainStates()
                        }
                        .onFailure {
                            Log.e(TAG, "enqueue failed: ${it::class.java.simpleName}: ${it.message}")
                            recordFailure("Capture drain scheduling failed", it)
                        }
                },
                Runnable::run
            )
        } catch (e: Exception) {
            Log.e(TAG, "scheduleDrain threw before enqueue: ${e::class.java.simpleName}: ${e.message}")
            recordFailure("Capture drain scheduling threw", e)
            throw e
        }
    }

    fun scheduleMaintenance(trigger: String) {
        Log.i(TAG, "scheduleMaintenance entered source=$trigger")
        workManager.enqueueUniqueWork(
            MAINTENANCE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CaptureProcessingWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CaptureRetryPolicy.WORKER_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    /** Live WorkInfo for the unique drain chain; consumed by the diagnostics screen. */
    fun observeDrainWork(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(DRAIN_WORK_NAME)

    /** Metadata-only snapshot of the drain chain state for device diagnosis. */
    private fun logDrainStates() {
        runCatching {
            workManager.getWorkInfosForUniqueWork(DRAIN_WORK_NAME)
                .get()
                .forEachIndexed { index, info ->
                    Log.i(
                        TAG,
                        "drain[$index] id=${info.id} state=${info.state} " +
                            "attempts=${info.runAttemptCount}"
                    )
                }
        }.onFailure { Log.w(TAG, "work-info query failed: ${it::class.java.simpleName}") }
    }

    private fun record(message: String, detail: String) {
        scope.launch {
            runCatching {
                activityLogRepository.append(
                    ActivityLogEntry(
                        category = ActivityCategory.CAPTURE_QUEUED,
                        message = message,
                        detail = detail,
                        taskId = null,
                        createdAt = timeProvider.now()
                    )
                )
            }
        }
    }

    private fun recordFailure(message: String, cause: Throwable) {
        scope.launch {
            runCatching {
                activityLogRepository.append(
                    ActivityLogEntry(
                        category = ActivityCategory.PROCESSING_FAILED,
                        message = message,
                        detail = "${cause::class.java.simpleName}: ${cause.message}",
                        taskId = null,
                        createdAt = timeProvider.now()
                    )
                )
            }
        }
    }

    private fun drainRequest() =
        OneTimeWorkRequestBuilder<CaptureProcessingWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CaptureRetryPolicy.WORKER_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    companion object {
        private const val TAG = "TaskMindCaptureWork"
        const val DRAIN_WORK_NAME = "capture-processing-drain"
        const val MAINTENANCE_WORK_NAME = "capture-processing-maintenance"
    }
}
