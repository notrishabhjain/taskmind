package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.intake.FakeNotificationCaptureRepository
import com.notrishabhjain.taskmind.domain.intake.FixedTimeProvider
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaptureProcessingCoordinatorTest {

    private val time = FixedTimeProvider(start = 1_000_000L)
    private val captures = FakeNotificationCaptureRepository()
    private val activityLog = FakeActivityLogRepository()
    private val tags = com.notrishabhjain.taskmind.domain.intake.FakeProjectTagRepository()

    private var processorResult: CaptureProcessingResult =
        CaptureProcessingResult.Deferred("not implemented")

    private var processor: NotificationCaptureProcessor = object : NotificationCaptureProcessor {
        override suspend fun process(capture: NotificationCapture): CaptureProcessingResult =
            processorResult
    }

    private class ThrowingProcessor(
        private val cause: Exception
    ) : NotificationCaptureProcessor {
        override suspend fun process(
            capture: NotificationCapture
        ): CaptureProcessingResult = throw cause
    }

    @Before
    fun setUp() {
        processorResult = CaptureProcessingResult.Deferred("not implemented")
    }

    private fun coordinator(maxRetries: Int = 3, batch: Int = 25) =
        CaptureProcessingCoordinator(
            captures = captures,
            processor = processor,
            activityLogRepository = activityLog,
            timeProvider = time,
            retryPolicy = com.notrishabhjain.taskmind.notification.CaptureRetryPolicy(maxRetries = maxRetries),
            batchSize = batch
        )

    private suspend fun seed(
        idempotencyKey: String = "k1",
        state: CaptureState = CaptureState.CAPTURED,
        retryCount: Int = 0,
        createdAtMillis: Long = 500L
    ): NotificationCapture {
        val base = NotificationCapture(
            idempotencyKey = idempotencyKey,
            sourcePackage = "com.whatsapp",
            notificationKey = "n-$idempotencyKey",
            postTime = Instant.ofEpochMilli(400L),
            canonicalSourceText = "canonical",
            contentHash = "hash-$idempotencyKey",
            sourceRef = "notification:com.whatsapp:n-$idempotencyKey",
            state = state,
            retryCount = retryCount,
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            updatedAt = Instant.ofEpochMilli(createdAtMillis)
        )
        return when (state) {
            CaptureState.CAPTURED ->
                (captures.insertIfAbsent(base) as com.notrishabhjain.taskmind.domain.repository.CaptureInsertOutcome.Inserted).capture

            else -> {
                val inserted = captures.insertIfAbsent(base) as com.notrishabhjain.taskmind.domain.repository.CaptureInsertOutcome.Inserted
                val forced = inserted.capture.copy(state = state, retryCount = retryCount)
                captures.update(forced)
                forced
            }
        }
    }

    @Test
    fun `captured is promoted to queued then claimed and deferred by stub`() = runBlocking {
        seed("k1")
        time.advanceBy(10)

        val summary = coordinator().runBatch()

        assertEquals(1, summary.promoted)
        assertEquals(1, summary.attempted)
        assertEquals(1, summary.deferredCount)
        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.DEFERRED, stored.state)
        assertEquals("not implemented", stored.lastError)
        assertEquals(1, activityLog.countOf(ActivityCategory.CAPTURE_DEFERRED))
    }

    @Test
    fun `claim skips terminal captures`() = runBlocking {
        seed("done", state = CaptureState.PROCESSED)
        time.advanceBy(5)

        val summary = coordinator().runBatch()

        assertEquals(0, summary.attempted)
        assertEquals(0, summary.skipped)
        assertEquals(CaptureState.PROCESSED, captures.findById(1L)!!.state)
        assertEquals(0, activityLog.countOf(ActivityCategory.PROCESSING_FAILED))
    }

    @Test
    fun `retryable failure increments retry count and schedules retry`() = runBlocking {
        seed("k-retry", retryCount = 1)
        processorResult = CaptureProcessingResult.RetryableFailure("transient io")

        coordinator(maxRetries = 3).runBatch()

        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.RETRY_PENDING, stored.state)
        assertEquals(2, stored.retryCount)
        assertEquals("transient io", stored.lastError)
        val scheduledRetries = activityLog.entries.count { entry ->
            entry.category == ActivityCategory.RETRY_SCHEDULED && entry.taskId == stored.id
        }
        assertEquals(1, scheduledRetries)
    }

    @Test
    fun `retry exhaustion transitions to failed permanently`() = runBlocking {
        seed("k-dead", retryCount = 3)
        processorResult = CaptureProcessingResult.RetryableFailure("still failing")

        coordinator(maxRetries = 3).runBatch()

        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.FAILED, stored.state)
        assertEquals(4, stored.retryCount)
        assertEquals(1, activityLog.countOf(ActivityCategory.PROCESSING_FAILED))
    }

    @Test
    fun `repeated retryable failures exhaust retries then fail without scheduling more`() = runBlocking {
        seed("k-exhaust", state = CaptureState.QUEUED)
        processorResult = CaptureProcessingResult.RetryableFailure("transient")
        val coordinator = coordinator(maxRetries = 2)

        coordinator.runBatch()
        var stored = captures.findById(1L)!!
        assertEquals(CaptureState.RETRY_PENDING, stored.state)
        assertEquals(1, stored.retryCount)

        coordinator.runBatch()
        stored = captures.findById(1L)!!
        assertEquals(CaptureState.RETRY_PENDING, stored.state)
        assertEquals(2, stored.retryCount)

        val finalSummary = coordinator.runBatch()
        stored = captures.findById(1L)!!
        assertEquals(CaptureState.FAILED, stored.state)
        assertEquals(3, stored.retryCount)
        assertEquals("transient", stored.lastError)
        assertEquals(1, finalSummary.failed)
        assertEquals(0, finalSummary.retried)
        assertEquals(
            2,
            activityLog.entries.count { entry ->
                entry.category == ActivityCategory.RETRY_SCHEDULED && entry.taskId == 1L
            }
        )
        assertEquals(1, activityLog.countOf(ActivityCategory.PROCESSING_FAILED))
    }

    @Test
    fun `repeated unexpected exceptions exhaust retries then fail without scheduling more`() = runBlocking {
        seed("k-bug-exhaust", state = CaptureState.QUEUED)
        processor = ThrowingProcessor(NullPointerException("programming bug"))
        val coordinator = coordinator(maxRetries = 2)

        coordinator.runBatch()
        var stored = captures.findById(1L)!!
        assertEquals(CaptureState.RETRY_PENDING, stored.state)
        assertEquals(1, stored.retryCount)
        assertEquals("NullPointerException", stored.lastError)

        coordinator.runBatch()
        stored = captures.findById(1L)!!
        assertEquals(CaptureState.RETRY_PENDING, stored.state)
        assertEquals(2, stored.retryCount)

        val finalSummary = coordinator.runBatch()
        stored = captures.findById(1L)!!
        assertEquals(CaptureState.FAILED, stored.state)
        assertEquals(3, stored.retryCount)
        assertEquals("NullPointerException", stored.lastError)
        assertEquals(1, finalSummary.failed)
        assertEquals(0, finalSummary.retried)

        val postFailure = coordinator.runBatch()
        assertEquals(0, postFailure.attempted)

        assertEquals(
            2,
            activityLog.entries.count { entry ->
                entry.category == ActivityCategory.PROCESSING_FAILED &&
                    entry.taskId == 1L && entry.message.contains("scheduled for retry")
            }
        )
        assertEquals(
            1,
            activityLog.entries.count { entry ->
                entry.category == ActivityCategory.PROCESSING_FAILED &&
                    entry.message.contains("failed permanently")
            }
        )
    }

    @Test
    fun `permanent failure goes straight to failed`() = runBlocking {
        seed("k-perm")
        processorResult = CaptureProcessingResult.PermanentFailure("unsupported payload shape")

        coordinator().runBatch()

        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.FAILED, stored.state)
        assertTrue(stored.lastError!!.contains("unsupported"))
        assertEquals(1, activityLog.countOf(ActivityCategory.PROCESSING_FAILED))
    }

    @Test
    fun `processed outcome records processedAt`() = runBlocking {
        seed("k-ok")
        processorResult = CaptureProcessingResult.Processed

        coordinator().runBatch()

        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.PROCESSED, stored.state)
        assertEquals(time.now().toEpochMilli(), stored.processedAt!!.toEpochMilli())
    }

    @Test
    fun `cancellation propagates without changing state`() = runBlocking {
        seed("k-cancel")
        processor = ThrowingProcessor(CancellationException("worker cancelled"))

        var cancelled = false
        try {
            coordinator().runBatch()
        } catch (e: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.PROCESSING, stored.state)
        assertEquals(0, stored.retryCount)
        assertEquals(0, activityLog.countOf(ActivityCategory.PROCESSING_FAILED))
    }

    @Test
    fun `unexpected processor exception becomes retryable`() = runBlocking {
        seed("k-bug")
        processor = ThrowingProcessor(NullPointerException("programming bug"))

        coordinator().runBatch()

        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.RETRY_PENDING, stored.state)
        assertEquals(1, stored.retryCount)
        assertEquals("NullPointerException", stored.lastError)
    }

    @Test
    fun `stale processing rows are recovered and drained in the same run`() = runBlocking {
        val stale = seed("k-stale", state = CaptureState.PROCESSING)
        captures.update(stale.copy(updatedAt = Instant.ofEpochMilli(100L)))
        time.advanceBy(60 * 60 * 1_000L)

        val summary = coordinator().runBatch()

        assertEquals(1, summary.recovered)
        assertEquals(1, summary.attempted)
        assertEquals(CaptureState.DEFERRED, captures.findById(stale.id)!!.state)
    }

    @Test
    fun `fresh processing rows are not recovered as stale`() = runBlocking {
        val fresh = seed("k-fresh", state = CaptureState.PROCESSING)
        captures.update(fresh.copy(updatedAt = time.now().minusSeconds(60)))

        val summary = coordinator().runBatch()

        assertEquals(0, summary.recovered)
        assertEquals(0, summary.attempted)
        assertEquals(CaptureState.PROCESSING, captures.findById(fresh.id)!!.state)
    }

    @Test
    fun `captured rows are promoted to queued before draining`() = runBlocking {
        seed("k-promote")
        time.advanceBy(10)

        val summary = coordinator().runBatch()

        assertEquals(1, summary.promoted)
        assertEquals(CaptureState.DEFERRED, captures.findById(1L)!!.state)
    }

    @Test
    fun `second run finds nothing new after all captures reached deferred`() = runBlocking {
        seed("k-once")
        val coordinator = coordinator()
        coordinator.runBatch()

        val second = coordinator.runBatch()

        assertEquals(0, second.attempted)
        assertEquals(1, captures.all.size)
    }
}
