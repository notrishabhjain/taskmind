package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import com.notrishabhjain.taskmind.domain.model.NotificationCaptureStateMachine
import com.notrishabhjain.taskmind.notification.CaptureRetryPolicy
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.repository.NotificationCaptureRepository
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

data class CaptureBatchSummary(
    val promoted: Int = 0,
    val recovered: Int = 0,
    val attempted: Int = 0,
    val processed: Int = 0,
    val deferredCount: Int = 0,
    val reviewed: Int = 0,
    val rejected: Int = 0,
    val retried: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val hasRetryableWork: Boolean = false
)

/**
 * Durable drain orchestration for captured notifications. The WorkManager
 * worker is a thin shell around [runBatch]; everything here is pure JVM.
 *
 * Invariants:
 * - state changes only through atomic claim + forward transitions validated by
 *   NotificationCaptureStateMachine
 * - CancellationException is never converted into a failure
 * - no notification content is written to the Activity Log
 */
class CaptureProcessingCoordinator(
    private val captures: NotificationCaptureRepository,
    private val processor: NotificationCaptureProcessor,
    private val activityLogRepository: ActivityLogRepository,
    private val timeProvider: TimeProvider,
    private val retryPolicy: CaptureRetryPolicy = CaptureRetryPolicy(),
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val processingTimeout: Duration = DEFAULT_PROCESSING_TIMEOUT
) {

    suspend fun runBatch(now: Instant = timeProvider.now()): CaptureBatchSummary {
        val promoted = captures.promoteCapturedToQueued(now)
        val staleCutoff = now.minus(processingTimeout)
        val recovered = captures.recoverStaleProcessing(staleCutoff, now)

        if (promoted > 0 || recovered > 0) {
            activityLogRepository.append(
                entry(
                    ActivityCategory.RETRY_SCHEDULED,
                    "Capture queue maintenance recovered pending work",
                    detail = "promoted=$promoted recovered=$recovered",
                    taskId = null,
                    at = now
                )
            )
        }

        var summary = CaptureBatchSummary(promoted = promoted, recovered = recovered)
        val due = captures.selectDueForProcessing(batchSize)
        summary = summary.copy(attempted = due.size)

        for (capture in due) {
            if (!captures.claimForProcessing(capture.id, now)) {
                summary = summary.copy(skipped = summary.skipped + 1)
                continue
            }
            val claimed = captures.findById(capture.id) ?: run {
                summary = summary.copy(skipped = summary.skipped + 1)
                null
            } ?: continue

            summary = try {
                applyProcessor(claimed, summary, now)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unexpected: Exception) {
                handleUnexpected(claimed, unexpected, summary, now)
            }
        }

        return summary
    }

    private suspend fun applyProcessor(
        claimed: NotificationCapture,
        summary: CaptureBatchSummary,
        now: Instant
    ): CaptureBatchSummary = when (val result = processor.process(claimed)) {
        is CaptureProcessingResult.Processed ->
            moveTo(claimed, CaptureState.PROCESSED, lastError = null, at = now, resultingTaskId = result.resultingTaskId)
                .let { summary.copy(processed = summary.processed + 1) }

        is CaptureProcessingResult.ReviewRequired ->
            moveTo(claimed, CaptureState.REVIEWED, lastError = null, at = now)
                .also {
                    activityLogRepository.append(
                        entry(ActivityCategory.INTAKE_SENT_TO_REVIEW, "Capture routed to review", "capture #${claimed.id}", claimed.id, now)
                    )
                }
                .let { summary.copy(reviewed = summary.reviewed + 1) }

        is CaptureProcessingResult.Rejected -> {
            moveTo(claimed, CaptureState.REJECTED, lastError = null, at = now)
            activityLogRepository.append(
                entry(ActivityCategory.INTAKE_REJECTED, "Capture rejected", "capture #${claimed.id}", claimed.id, now)
            )
            summary.copy(rejected = summary.rejected + 1)
        }

        is CaptureProcessingResult.Deferred -> {
            moveTo(claimed, CaptureState.DEFERRED, lastError = result.reason?.take(200), at = now)
            activityLogRepository.append(
                entry(
                    ActivityCategory.CAPTURE_DEFERRED,
                    "Notification capture deferred",
                    detail = "capture #${claimed.id} ${result.reason.orEmpty()}".trim(),
                    taskId = claimed.id,
                    at = now
                )
            )
            summary.copy(deferredCount = summary.deferredCount + 1)
        }

        is CaptureProcessingResult.RetryableFailure ->
            scheduleRetry(claimed, result.reason, summary, now)

        is CaptureProcessingResult.PermanentFailure ->
            failPermanently(claimed, result.reason, summary, now)
    }

    private suspend fun scheduleRetry(
        claimed: NotificationCapture,
        reason: String?,
        summary: CaptureBatchSummary,
        now: Instant
    ): CaptureBatchSummary {
        val nextRetryCount = claimed.retryCount + 1
        val updatedAt = timeProvider.now()

        if (retryPolicy.isRetryExhausted(nextRetryCount)) {
            return exhaustRetries(claimed, reason, nextRetryCount, updatedAt, summary, now)
        }

        NotificationCaptureStateMachine.requireValidTransition(claimed.state, CaptureState.RETRY_PENDING)
        captures.update(
            claimed.copy(
                state = CaptureState.RETRY_PENDING,
                retryCount = nextRetryCount,
                lastError = reason,
                updatedAt = updatedAt
            )
        )
        activityLogRepository.append(
            entry(
                ActivityCategory.RETRY_SCHEDULED,
                "Notification capture scheduled for retry",
                detail = "capture #${claimed.id} retry=$nextRetryCount",
                taskId = claimed.id,
                at = now
            )
        )
        return summary.copy(retried = summary.retried + 1, hasRetryableWork = true)
    }

    private suspend fun exhaustRetries(
        claimed: NotificationCapture,
        reason: String?,
        nextRetryCount: Int,
        updatedAt: Instant,
        summary: CaptureBatchSummary,
        now: Instant
    ): CaptureBatchSummary {
        NotificationCaptureStateMachine.requireValidTransition(claimed.state, CaptureState.FAILED)
        captures.update(
            claimed.copy(
                state = CaptureState.FAILED,
                retryCount = nextRetryCount,
                lastError = reason,
                updatedAt = updatedAt
            )
        )
        activityLogRepository.append(
            entry(
                ActivityCategory.PROCESSING_FAILED,
                "Notification capture failed permanently after $nextRetryCount attempts",
                detail = "capture #${claimed.id} retries=$nextRetryCount",
                taskId = claimed.id,
                at = now
            )
        )
        return summary.copy(failed = summary.failed + 1)
    }

    private suspend fun failPermanently(
        claimed: NotificationCapture,
        reason: String?,
        summary: CaptureBatchSummary,
        now: Instant
    ): CaptureBatchSummary {
        NotificationCaptureStateMachine.requireValidTransition(claimed.state, CaptureState.FAILED)
        val updatedAt = timeProvider.now()
        captures.update(
            claimed.copy(state = CaptureState.FAILED, lastError = reason, updatedAt = updatedAt)
        )
        activityLogRepository.append(
            entry(
                ActivityCategory.PROCESSING_FAILED,
                "Notification capture failed permanently",
                detail = "capture #${claimed.id} ${reason.orEmpty()}".trim(),
                taskId = claimed.id,
                at = now
            )
        )
        return summary.copy(failed = summary.failed + 1)
    }

    private suspend fun handleUnexpected(
        claimed: NotificationCapture,
        unexpected: Exception,
        summary: CaptureBatchSummary,
        now: Instant
    ): CaptureBatchSummary {
        // Unexpected errors stay retryable so no capture is lost to a bug; only
        // the safe exception class name is recorded, never notification content.
        val nextRetryCount = claimed.retryCount + 1

        if (retryPolicy.isRetryExhausted(nextRetryCount)) {
            return exhaustRetries(
                claimed = claimed,
                reason = unexpected::class.simpleName,
                nextRetryCount = nextRetryCount,
                updatedAt = timeProvider.now(),
                summary = summary,
                now = now
            )
        }

        val updatedAt = timeProvider.now()
        captures.update(
            claimed.copy(
                state = CaptureState.RETRY_PENDING,
                retryCount = nextRetryCount,
                lastError = unexpected::class.simpleName,
                updatedAt = updatedAt
            )
        )
        activityLogRepository.append(
            entry(
                ActivityCategory.PROCESSING_FAILED,
                "Notification capture hit an unexpected error and was scheduled for retry",
                detail = "capture #${claimed.id} ${unexpected::class.simpleName}",
                taskId = claimed.id,
                at = now
            )
        )
        return summary.copy(retried = summary.retried + 1, hasRetryableWork = true)
    }

    private suspend fun moveTo(
        claimed: NotificationCapture,
        to: CaptureState,
        lastError: String?,
        at: Instant,
        resultingTaskId: Long? = null
    ): NotificationCapture {
        NotificationCaptureStateMachine.requireValidTransition(claimed.state, to)
        val updated = claimed.copy(
            state = to,
            lastError = lastError,
            updatedAt = at,
            processedAt = if (to == CaptureState.PROCESSED) at else claimed.processedAt,
            resultingTaskId = resultingTaskId ?: claimed.resultingTaskId
        )
        captures.update(updated)
        return updated
    }

    private fun entry(
        category: ActivityCategory,
        message: String,
        detail: String?,
        taskId: Long?,
        at: Instant
    ): ActivityLogEntry = ActivityLogEntry(
        category = category,
        message = message,
        detail = detail,
        taskId = taskId,
        createdAt = at
    )

    companion object {
        const val DEFAULT_BATCH_SIZE = 25

        /** PROCESSING rows untouched longer than this are considered dead workers. */
        val DEFAULT_PROCESSING_TIMEOUT: Duration = Duration.ofMinutes(15)
    }
}
