package com.notrishabhjain.taskmind.data.mapper

import com.notrishabhjain.taskmind.data.db.entity.NotificationCaptureEntity
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NotificationCaptureMappersTest {

    private val capturedAt: Instant = Instant.ofEpochMilli(1_700_000_000_000L)
    private val updatedAt: Instant = Instant.ofEpochMilli(1_700_000_123_456L)
    private val postTime: Instant = Instant.ofEpochMilli(1_699_999_999_999L)
    private val processedAt: Instant = Instant.ofEpochMilli(1_700_000_050_000L)

    @Test
    fun `fully populated capture round-trips through entity`() {
        val capture = NotificationCapture(
            id = 31L,
            idempotencyKey = "sha256:abc123",
            sourcePackage = "com.whatsapp",
            sourceAppLabel = "WhatsApp",
            notificationKey = "0|com.whatsapp|101|null|10001",
            notificationId = 101,
            notificationTag = "msg-tag",
            postTime = postTime,
            title = "Dad",
            text = "Please pay the electricity bill today",
            bigText = "Please pay the electricity bill today before 6pm",
            subText = "WhatsApp",
            infoText = "2 new messages",
            conversationTitle = "Family group",
            category = "msg",
            channelLabel = "Messages",
            canonicalSourceText = "Dad\nPlease pay the electricity bill today",
            contentHash = "hash:deadbeef",
            sourceRef = "notification:com.whatsapp:0|com.whatsapp|101|null|10001",
            state = CaptureState.QUEUED,
            retryCount = 1,
            lastError = "previous attempt failed",
            resultingTaskId = null,
            processedAt = null,
            createdAt = capturedAt,
            updatedAt = updatedAt
        )

        assertEquals(capture, capture.toEntity().toDomain())
    }

    @Test
    fun `nullable fields survive as null and defaults hold`() {
        val capture = NotificationCapture(
            idempotencyKey = "key",
            sourcePackage = "com.example",
            notificationKey = "key-1",
            postTime = postTime,
            canonicalSourceText = "Title\nBody",
            contentHash = "h",
            sourceRef = "notification:com.example:key-1",
            createdAt = capturedAt,
            updatedAt = capturedAt
        )

        val entity: NotificationCaptureEntity = capture.toEntity()

        assertNull(entity.sourceAppLabel)
        assertNull(entity.notificationId)
        assertNull(entity.notificationTag)
        assertNull(entity.title)
        assertNull(entity.text)
        assertNull(entity.bigText)
        assertNull(entity.subText)
        assertNull(entity.infoText)
        assertNull(entity.conversationTitle)
        assertNull(entity.category)
        assertNull(entity.channelLabel)
        assertNull(entity.lastError)
        assertNull(entity.resultingTaskId)
        assertNull(entity.processedAt)

        val restored = entity.toDomain()
        assertEquals(capture, restored)
        assertEquals(CaptureState.CAPTURED, restored.state)
        assertEquals(0, restored.retryCount)
    }

    @Test
    fun `timestamps convert through epoch millis without drift`() {
        val capture = NotificationCapture(
            idempotencyKey = "k",
            sourcePackage = "p",
            notificationKey = "n",
            postTime = postTime,
            canonicalSourceText = "s",
            contentHash = "c",
            sourceRef = "r",
            processedAt = processedAt,
            createdAt = capturedAt,
            updatedAt = updatedAt
        )

        val entity = capture.toEntity()

        assertEquals(postTime.toEpochMilli(), entity.postTime)
        assertEquals(processedAt.toEpochMilli(), entity.processedAt)
        assertEquals(capturedAt.toEpochMilli(), entity.createdAt)
        assertEquals(updatedAt.toEpochMilli(), entity.updatedAt)

        val restored = entity.toDomain()
        assertEquals(postTime, restored.postTime)
        assertEquals(processedAt, restored.processedAt)
        assertEquals(capturedAt, restored.createdAt)
        assertEquals(updatedAt, restored.updatedAt)
    }

    @Test
    fun `all capture states round-trip`() {
        CaptureState.entries.forEach { state ->
            val restored = NotificationCapture(
                idempotencyKey = "k",
                sourcePackage = "p",
                notificationKey = "n",
                postTime = postTime,
                canonicalSourceText = "s",
                contentHash = "c",
                sourceRef = "r",
                state = state,
                createdAt = capturedAt,
                updatedAt = capturedAt
            ).toEntity().toDomain().state

            assertEquals(state, restored)
        }
    }

    @Test
    fun `malformed stored state fails loudly instead of inventing a fallback`() {
        val corrupted = NotificationCapture(
            idempotencyKey = "k",
            sourcePackage = "p",
            notificationKey = "n",
            postTime = postTime,
            canonicalSourceText = "s",
            contentHash = "c",
            sourceRef = "r",
            state = CaptureState.CAPTURED,
            createdAt = capturedAt,
            updatedAt = capturedAt
        ).toEntity()
            .copy(state = "NOT_A_REAL_STATE")

        assertThrows(IllegalStateException::class.java) { corrupted.toDomain() }
    }
}
