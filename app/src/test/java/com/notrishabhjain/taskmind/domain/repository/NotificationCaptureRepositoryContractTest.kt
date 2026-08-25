package com.notrishabhjain.taskmind.domain.repository

import com.notrishabhjain.taskmind.domain.intake.FakeNotificationCaptureRepository
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationCaptureRepositoryContractTest {

    private lateinit var repository: FakeNotificationCaptureRepository

    @Before
    fun setUp() {
        repository = FakeNotificationCaptureRepository()
    }

    private fun capture(
        idempotencyKey: String = "key-1",
        state: CaptureState = CaptureState.CAPTURED
    ): NotificationCapture = NotificationCapture(
        idempotencyKey = idempotencyKey,
        sourcePackage = "com.whatsapp",
        notificationKey = "0|com.whatsapp|101|null|10001",
        postTime = Instant.ofEpochMilli(1_700_000_000_000L),
        canonicalSourceText = "Dad\nPlease pay the electricity bill today",
        contentHash = "hash:$idempotencyKey",
        sourceRef = "notification:com.whatsapp:0|com.whatsapp|101|null|10001",
        state = state,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )

    @Test
    fun `insert stores a new capture and returns it`() = runBlocking {
        val outcome = repository.insertIfAbsent(capture())

        assertTrue(outcome is CaptureInsertOutcome.Inserted)
        assertEquals(1, repository.all.size)
    }

    @Test
    fun `duplicate idempotency key returns the existing capture without inserting`() = runBlocking {
        val first = repository.insertIfAbsent(capture())
        val second = repository.insertIfAbsent(capture(idempotencyKey = "key-1"))

        assertTrue(first is CaptureInsertOutcome.Inserted)
        assertTrue(second is CaptureInsertOutcome.AlreadyCaptured)
        assertEquals(
            (first as CaptureInsertOutcome.Inserted).capture.id,
            (second as CaptureInsertOutcome.AlreadyCaptured).existing.id
        )
        assertEquals(1, repository.all.size)
    }

    @Test
    fun `lookups resolve by id and by idempotency key`() = runBlocking {
        val inserted = (repository.insertIfAbsent(capture()) as CaptureInsertOutcome.Inserted).capture

        assertEquals(inserted, repository.findById(inserted.id))
        assertEquals(inserted, repository.findByIdempotencyKey("key-1"))
        assertNull(repository.findById(999L))
        assertNull(repository.findByIdempotencyKey("missing"))
    }

    @Test
    fun `state transitions persist through update`() = runBlocking {
        val inserted = (repository.insertIfAbsent(capture()) as CaptureInsertOutcome.Inserted).capture

        val queued = inserted.copy(state = CaptureState.QUEUED, updatedAt = inserted.updatedAt)
        repository.update(queued)
        assertEquals(CaptureState.QUEUED, repository.findById(inserted.id)!!.state)

        val processing = queued.copy(
            state = CaptureState.PROCESSING,
            retryCount = 1,
            lastError = "transient worker failure"
        )
        repository.update(processing)

        val processed = processing.copy(
            state = CaptureState.PROCESSED,
            resultingTaskId = 42L,
            processedAt = java.time.Instant.ofEpochMilli(2_000L),
            retryCount = 0,
            lastError = null
        )
        repository.update(processed)

        val stored = repository.findById(inserted.id)!!
        assertEquals(CaptureState.PROCESSED, stored.state)
        assertEquals(42L, stored.resultingTaskId!!)
        assertEquals(0, stored.retryCount)
        assertEquals(null, stored.lastError)
    }

    @Test
    fun `observeByState emits current snapshot filtered by state`() = runBlocking {
        repository.insertIfAbsent(capture(idempotencyKey = "captured-a"))
        repository.insertIfAbsent(
            capture(idempotencyKey = "processed-a", state = CaptureState.PROCESSED)
        )

        val capturedRows = repository.observeByState(CaptureState.CAPTURED).first()

        assertEquals(1, capturedRows.size)
        assertEquals("captured-a", capturedRows.single().idempotencyKey)
    }

    @Test
    fun `observeStateCounts reports live per-state counts`() = runBlocking {
        repository.insertIfAbsent(capture(idempotencyKey = "captured-a"))
        repository.insertIfAbsent(capture(idempotencyKey = "captured-b"))
        repository.insertIfAbsent(
            capture(idempotencyKey = "processed-a", state = CaptureState.PROCESSED)
        )

        val counts = repository.observeStateCounts().first()

        assertEquals(2, counts[CaptureState.CAPTURED])
        assertEquals(1, counts[CaptureState.PROCESSED])
        assertEquals(null, counts[CaptureState.FAILED])
    }

    @Test
    fun `observeStateCounts reacts to state updates`() = runBlocking {
        val inserted = (repository.insertIfAbsent(capture()) as CaptureInsertOutcome.Inserted).capture

        repository.update(inserted.copy(state = CaptureState.REJECTED))

        val counts = repository.observeStateCounts().first()
        assertEquals(1, counts[CaptureState.REJECTED])
        assertEquals(null, counts[CaptureState.CAPTURED])
    }
}
