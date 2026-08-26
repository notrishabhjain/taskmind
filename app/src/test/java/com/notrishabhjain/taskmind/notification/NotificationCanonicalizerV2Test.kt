package com.notrishabhjain.taskmind.notification

import com.notrishabhjain.taskmind.domain.intake.EvidenceCheck
import com.notrishabhjain.taskmind.domain.intake.EvidenceValidator
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCanonicalizerV2Test {

    private val zone = ZoneId.of("UTC")

    private fun messagingSnapshot(
        latestText: String = "Please call Rahul tomorrow at 4 PM",
        historic: Boolean = false
    ) = NotificationSnapshot(
        packageName = "com.whatsapp",
        notificationKey = "0|com.whatsapp|101|null|10001",
        notificationId = 10001,
        tag = null,
        postTimeMs = 1_700_000_000_000L,
        appLabel = "WhatsApp",
        title = "Rahul",
        text = latestText,
        bigText = null,
        subText = null,
        infoText = null,
        summaryText = null,
        conversation = ConversationContext(
            title = "Rahul",
            isGroup = false,
            messages = listOf(MessageEntry(sender = "Rahul", text = latestText, timestampMs = 42L, historic = historic))
        ),
        category = "msg",
        channelId = "whatsapp-messages",
        style = NotificationStyle.MESSAGING,
        isOngoing = false,
        groupKey = null,
        flags = 0,
        extrasCensus = ExtrasCensus(emptyList())
    )

    @Test
    fun `toCapture maps identity presentation and provenance fields`() {
        val capture = NotificationCanonicalizer.toCapture(messagingSnapshot(), Instant.ofEpochMilli(5L))

        assertEquals("com.whatsapp", capture.sourcePackage)
        assertEquals("WhatsApp", capture.sourceAppLabel)
        assertEquals("0|com.whatsapp|101|null|10001", capture.notificationKey)
        assertEquals(10001, capture.notificationId)
        assertEquals(1_700_000_000_000L, capture.postTime.toEpochMilli())
        assertEquals("Rahul", capture.title)
        assertEquals("Rahul", capture.conversationTitle)
        assertEquals("msg", capture.category)
        assertEquals("whatsapp-messages", capture.channelLabel)
        assertEquals(com.notrishabhjain.taskmind.domain.model.CaptureState.CAPTURED, capture.state)
        assertEquals(Instant.ofEpochMilli(5L), capture.createdAt)
    }

    @Test
    fun `hash idempotency and sourceRef are stable for identical snapshots`() {
        val first = NotificationCanonicalizer.toCapture(messagingSnapshot(), Instant.ofEpochMilli(1L))
        val second = NotificationCanonicalizer.toCapture(messagingSnapshot(), Instant.ofEpochMilli(2L))

        assertEquals(first.contentHash, second.contentHash)
        assertEquals(first.idempotencyKey, second.idempotencyKey)
        assertEquals(first.canonicalSourceText, second.canonicalSourceText)
        assertEquals(
            "notification:com.whatsapp:0|com.whatsapp|101|null|10001",
            first.sourceRef
        )
    }

    @Test
    fun `content change produces a different hash enabling versioning`() {
        val original = NotificationCanonicalizer.toCapture(
            messagingSnapshot(latestText = "Please call Rahul tomorrow at 4 PM"),
            Instant.ofEpochMilli(1L)
        )
        val updated = NotificationCanonicalizer.toCapture(
            messagingSnapshot(latestText = "Please call Rahul tomorrow at 5 PM"),
            Instant.ofEpochMilli(2L)
        )

        assertTrue(original.contentHash != updated.contentHash)
        assertTrue(original.idempotencyKey != updated.idempotencyKey)
    }

    @Test
    fun `canonical text preserves sender boundaries for evidence validation`() {
        val capture = NotificationCanonicalizer.toCapture(messagingSnapshot(), Instant.ofEpochMilli(1L))

        val check = EvidenceValidator.validate(
            evidence = "Rahul: Please call Rahul tomorrow at 4 PM",
            sourceText = capture.canonicalSourceText
        )
        assertEquals(EvidenceCheck.Valid, check)
    }

    @Test
    fun `legacy raw-input path remains available alongside V2`() {
        val legacyInput = RawNotificationInput(
            sourcePackage = "com.whatsapp",
            notificationKey = "k",
            postTimeMillis = 10L,
            text = "hello"
        )
        val capture = NotificationCanonicalizer.toCapture(legacyInput, Instant.ofEpochMilli(1L))
        assertEquals("notification:com.whatsapp:k", capture.sourceRef)
        assertTrue(capture.canonicalSourceText.contains("TEXT: hello"))
    }
}