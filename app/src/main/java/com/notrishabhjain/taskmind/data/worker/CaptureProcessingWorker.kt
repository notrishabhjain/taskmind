package com.notrishabhjain.taskmind.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.service.CaptureBatchSummary
import com.notrishabhjain.taskmind.domain.service.CaptureProcessingCoordinator
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import kotlinx.coroutines.CancellationException

/**
 * Thin WorkManager shell around [CaptureProcessingCoordinator]. All durable
 * logic lives in the coordinator so it stays pure-JVM testable.
 *
 * Strategy: bounded-drain. Each execution claims/processes up to the
 * coordinator's batch size; when a batch fills up, a follow-up unique drain is
 * scheduled so bursts are processed without unbounded memory.
 */
class CaptureProcessingWorker(
    context: Context,
    parameters: WorkerParameters,
    private val coordinator: CaptureProcessingCoordinator,
    private val activityLogRepository: ActivityLogRepository,
    private val timeProvider: TimeProvider
) : CoroutineWorker(context, parameters) {

    /**
     * Reflection fallback used by WorkManager's default factory when the
     * custom configuration/factory was not applied (e.g. default initializer
     * won initialization). Dependencies resolve from the application
     * container so the pipeline works under either initialization path.
     */
    constructor(context: Context, parameters: WorkerParameters) : this(
        context,
        parameters,
        (context.applicationContext as com.notrishabhjain.taskmind.TaskMindApplication)
            .container.captureProcessingCoordinator,
        (context.applicationContext as com.notrishabhjain.taskmind.TaskMindApplication)
            .container.activityLogRepository,
        (context.applicationContext as com.notrishabhjain.taskmind.TaskMindApplication)
            .container.timeProvider
    ) {
        creationPath = "reflection-fallback"
        Log.w(TAG, "worker created via REFLECTION fallback (custom factory bypassed)")
    }

    private var creationPath = "custom-factory"

    init {
        Log.i(TAG, "worker constructed via $creationPath")
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork entered attempt=$runAttemptCount")
        logDrainStarted()
        val summary = try {
            coordinator.runBatch()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unexpected: Exception) {
            // Batch-level catastrophe; rows keep their last durable state and
            // WM exponential backoff reschedules the drain.
            activityLogRepository.append(
                entry(
                    category = ActivityCategory.PROCESSING_FAILED,
                    message = "Capture drain batch failed",
                    detail = "attempt=$runAttemptCount class=${unexpected::class.java.simpleName}"
                )
            )
            return Result.retry()
        }
        logDrainFinished(summary)

        if (summary.hasRetryableWork && runAttemptCount < MAX_WM_ATTEMPTS_BEFORE_BACKOFF_RESET) {
            // Domain-level RETRY_PENDING was already persisted; WM backoff
            // spaces out the next attempt.
            Log.i(TAG, "doWork exit -> retry (hasRetryableWork)")
            return Result.retry()
        }

        if (summary.attempted >= DRAIN_BATCH_SIZE) {
            scheduleFollowUpDrain()
        }

        Log.i(TAG, "doWork exit -> success")
        return Result.success()
    }

    /** Metadata-only diagnostics: counters and attempt numbers, never content. */
    private suspend fun logDrainStarted() {
        activityLogRepository.append(
            entry(
                category = ActivityCategory.CAPTURE_QUEUED,
                message = "Capture drain started",
                detail = "attempt=$runAttemptCount createdVia=$creationPath worker=CaptureProcessingWorker"
            )
        )
    }

    private suspend fun logDrainFinished(summary: CaptureBatchSummary) {
        activityLogRepository.append(
            entry(
                category = if (summary.processed > 0) ActivityCategory.CAPTURE_PROCESSED else ActivityCategory.CAPTURE_QUEUED,
                message = "Capture drain finished",
                detail = "attempted=${summary.attempted} processed=${summary.processed} " +
                    "reviewed=${summary.reviewed} deferred=${summary.deferredCount} " +
                    "retried=${summary.retried} failed=${summary.failed}"
            )
        )
    }

    private fun entry(category: ActivityCategory, message: String, detail: String) = ActivityLogEntry(
        category = category,
        message = message,
        detail = detail,
        taskId = null,
        createdAt = timeProvider.now()
    )

    private fun scheduleFollowUpDrain() {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            FOLLOW_UP_DRAIN_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CaptureProcessingWorker>().build()
        )
    }

    companion object {

        private const val TAG = "TaskMindCaptureWork"

        /** Must match CaptureProcessingCoordinator.DEFAULT_BATCH_SIZE semantics. */
        const val DRAIN_BATCH_SIZE = 25

        /**
         * After this many consecutive WM attempts for the same work we stop
         * short-circuiting with retry() so the work can reach a terminal state;
         * domain-level RETRY_PENDING rows are re-claimed by the next drain
         * scheduled through maintenance/app-open paths.
         */
        const val MAX_WM_ATTEMPTS_BEFORE_BACKOFF_RESET = 10

        const val FOLLOW_UP_DRAIN_NAME = "capture-processing-follow-up-drain"

        fun factory(
            coordinatorProvider: () -> CaptureProcessingCoordinator,
            activityLogRepository: ActivityLogRepository,
            timeProvider: TimeProvider
        ): WorkerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    Log.i(TAG, "custom factory creating $workerClassName")
                    return CaptureProcessingWorker(
                        appContext,
                        workerParameters,
                        coordinatorProvider(),
                        activityLogRepository,
                        timeProvider
                    )
                }
            }
    }
}
