package com.notrishabhjain.taskmind.notification

import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureDeduplicationTest {

    private val at: Instant = Instant.ofEpochMilli(1_700_000_000_000L)

    private fun capture(
        sourcePackage: String = "com.whatsapp",
        notificationKey: String = "0|com.whatsapp|101|null|10001",
        contentHash: String,
        postTimeMillis: Long = 1_700_000_000_000L
    ): NotificationCapture = NotificationCapture(
        id = 0L,
        idempotencyKey = "$sourcePackage|$notificationKey|$postTimeMillis|$contentHash",
        sourcePackage = sourcePackage,
        notificationKey = notificationKey,
        postTime = Instant.ofEpochMilli(postTimeMillis),
        canonicalSourceText = "canonical:$contentHash",
        contentHash = contentHash,
        sourceRef = "notification:$sourcePackage:$notificationKey",
        state = CaptureState.CAPTURED,
        createdAt = at,
        updatedAt = at
    )

    @Test
    fun `no existing capture is a fresh capture`() {
        val incoming = capture(contentHash = "h1")

        assertEquals(
            CaptureRelation.FRESH_CAPTURE,
            CaptureDeduplication.classify(existing = null, incoming = incoming)
        )
    }

    @Test
    fun `identical capture is an exact duplicate`() {
        val existing = capture(contentHash = "h1")
        val incoming = existing.copy(id = 999L)

        assertEquals(
            CaptureRelation.EXACT_DUPLICATE,
            CaptureDeduplication.classify(existing, incoming)
        )
    }

    @Test
    fun `same identity with same hash is an exact duplicate`() {
        val existing = capture(contentHash = "same-hash")
        val incoming = existing.copy(
            id = 2L,
            title = "irrelevant field differs"
        )

        assertEquals(CaptureRelation.EXACT_DUPLICATE, CaptureDeduplication.classify(existing, incoming))
    }

    @Test
    fun `same identity with changed canonical text is a new version`() {
        val existing = capture(contentHash = "hash-v1")
        val incoming = existing.copy(contentHash = "hash-v2", canonicalSourceText = "changed")

        assertEquals(CaptureRelation.NEW_VERSION, CaptureDeduplication.classify(existing, incoming))
    }

    @Test
    fun `changed content hash alone produces a new version`() {
        val existing = capture(contentHash = "hash-a")
        val incoming = existing.copy(contentHash = "hash-b")

        assertEquals(CaptureRelation.NEW_VERSION, CaptureDeduplication.classify(existing, incoming))
    }

    @Test
    fun `changed notification key is a new logical capture`() {
        val existing = capture(contentHash = "h")
        val incoming = existing.copy(notificationKey = "0|com.whatsapp|202|null|10001")

        assertEquals(CaptureRelation.FRESH_CAPTURE, CaptureDeduplication.classify(existing, incoming))
    }

    @Test
    fun `changed package is a new logical capture`() {
        val existing = capture(contentHash = "h")
        val incoming = existing.copy(sourcePackage = "com.telegram")

        assertEquals(CaptureRelation.FRESH_CAPTURE, CaptureDeduplication.classify(existing, incoming))
    }

    @Test
    fun `version preserves its own provenance and does not overwrite the previous capture`() {
        val existing = capture(contentHash = "v1").copy(state = CaptureState.PROCESSED)
        val incomingVersion2 = capture(contentHash = "v2").copy(
            state = CaptureState.CAPTURED,
            canonicalSourceText = "updated body",
            createdAt = at.plusSeconds(60),
            updatedAt = at.plusSeconds(60)
        )

        assertEquals("v1", existing.contentHash)
        assertEquals(CaptureState.PROCESSED, existing.state)

        assertEquals("v2", incomingVersion2.contentHash)
        assertEquals(CaptureState.CAPTURED, incomingVersion2.state)
        assertEquals("updated body", incomingVersion2.canonicalSourceText)
    }
}
