package com.notrishabhjain.taskmind.domain.repository

import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import kotlinx.coroutines.flow.Flow
import java.time.Instant

sealed interface CaptureInsertOutcome {
    data class Inserted(val capture: NotificationCapture) : CaptureInsertOutcome

    data class AlreadyCaptured(val existing: NotificationCapture) : CaptureInsertOutcome
}

interface NotificationCaptureRepository {

    suspend fun insertIfAbsent(capture: NotificationCapture): CaptureInsertOutcome

    suspend fun findById(id: Long): NotificationCapture?

    suspend fun findByIdempotencyKey(idempotencyKey: String): NotificationCapture?

    suspend fun update(capture: NotificationCapture)

    fun observeByState(state: CaptureState): Flow<List<NotificationCapture>>

    fun observeRecentCaptures(limit: Int): Flow<List<NotificationCapture>>

    fun observeCapture(id: Long): Flow<NotificationCapture?>

    suspend fun findLatestByIdentity(
        sourcePackage: String,
        notificationKey: String
    ): NotificationCapture?

    /**
     * Atomically moves an eligible capture (CAPTURED / QUEUED / RETRY_PENDING)
     * into PROCESSING. Returns false when the capture is missing, already
     * terminal, or claimed by another worker — the caller must then skip it.
     */
    suspend fun claimForProcessing(id: Long, now: Instant): Boolean

    /** Oldest-first due captures for the drain worker, bounded by [limit]. */
    suspend fun selectDueForProcessing(limit: Int): List<NotificationCapture>

    /** CAPTURED → QUEUED for every lingering captured row; returns affected count. */
    suspend fun promoteCapturedToQueued(now: Instant): Int

    /**
     * Recovers PROCESSING rows whose updatedAt is older than [staleCutoff]
     * (process died mid-processing) back to QUEUED; returns affected count.
     */
    suspend fun recoverStaleProcessing(staleCutoff: Instant, now: Instant): Int
}
