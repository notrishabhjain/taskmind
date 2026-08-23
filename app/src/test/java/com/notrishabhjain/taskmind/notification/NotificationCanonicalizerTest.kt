package com.notrishabhjain.taskmind.notification

import com.notrishabhjain.taskmind.domain.model.CaptureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCanonicalizerTest {

    private val base = RawNotificationInput(
        sourcePackage = "com.whatsapp",
        notificationKey = "0|com.whatsapp|101|null|10001",
        postTimeMillis = 1_700_000_000_000L,
        title = "Dad",
        text = "Please pay the electricity bill today",
        bigText = null,
        subText = null,
        infoText = null,
        conversationTitle = null,
        category = "msg",
        appLabel = "WhatsApp"
    )

    @Test
    fun `canonical source text joins present fields in fixed order`() {
        val input = base.copy(
            conversationTitle = "Family group",
            bigText = "Please pay the electricity bill today before 6pm",
            subText = "WhatsApp"
        )

        assertEquals(
            "Family group\nDad\nPlease pay the electricity bill today\n" +
                "Please pay the electricity bill today before 6pm\nWhatsApp",
            NotificationCanonicalizer.canonicalSourceText(input)
        )
    }

    @Test
    fun `absent fields are omitted cleanly`() {
        val minimal = base.copy(
            title = "Alarm",
            text = null,
            bigText = null,
            subText = null,
            infoText = null,
            conversationTitle = null
        )

        assertEquals("Alarm", NotificationCanonicalizer.canonicalSourceText(minimal))
    }

    @Test
    fun `all text fields absent produces empty canonical text`() {
        val empty = base.copy(
            title = null, text = null, bigText = null,
            subText = null, infoText = null, conversationTitle = null
        )

        assertEquals("", NotificationCanonicalizer.canonicalSourceText(empty))
    }

    @Test
    fun `stored canonical text is verbatim and not normalized`() {
        val messy = base.copy(text = "  Pay   bill!!  ")

        assertTrue(
            NotificationCanonicalizer.canonicalSourceText(messy).contains("  Pay   bill!!  ")
        )
    }

    @Test
    fun `content hash is stable for identical inputs`() {
        assertEquals(
            NotificationCanonicalizer.contentHash(base),
            NotificationCanonicalizer.contentHash(base.copy())
        )
    }

    @Test
    fun `content hash changes when any text field changes`() {
        val changedTitle = base.copy(title = "Mom")
        val changedBigText = base.copy(bigText = "extra detail")

        assertNotEquals(NotificationCanonicalizer.contentHash(base), NotificationCanonicalizer.contentHash(changedTitle))
        assertNotEquals(NotificationCanonicalizer.contentHash(base), NotificationCanonicalizer.contentHash(changedBigText))
    }

    @Test
    fun `idempotency key is stable for identical deliveries`() {
        assertEquals(
            NotificationCanonicalizer.idempotencyKey(base),
            NotificationCanonicalizer.idempotencyKey(base.copy())
        )
    }

    @Test
    fun `idempotency key differs when source package post time or content differs`() {
        assertNotEquals(
            NotificationCanonicalizer.idempotencyKey(base),
            NotificationCanonicalizer.idempotencyKey(base.copy(sourcePackage = "com.telegram"))
        )
        assertNotEquals(
            NotificationCanonicalizer.idempotencyKey(base),
            NotificationCanonicalizer.idempotencyKey(base.copy(postTimeMillis = 1L))
        )
        assertNotEquals(
            NotificationCanonicalizer.idempotencyKey(base),
            NotificationCanonicalizer.idempotencyKey(base.copy(text = "different"))
        )
    }

    @Test
    fun `hashes are sha-256 hex digests`() {
        val hash = NotificationCanonicalizer.contentHash(base)

        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `self notifications are detected by exact package match`() {
        assertTrue(NotificationCanonicalizer.isSelfNotification("com.notrishabhjain.taskmind", "com.notrishabhjain.taskmind"))
        assertFalse(NotificationCanonicalizer.isSelfNotification("com.whatsapp", "com.notrishabhjain.taskmind"))
    }

    @Test
    fun `toCapture builds a captured-state domain model with derived fields`() {
        val capture = NotificationCanonicalizer.toCapture(base, capturedAt = java.time.Instant.ofEpochMilli(9_999L))

        assertEquals(CaptureState.CAPTURED, capture.state)
        assertEquals("com.whatsapp", capture.sourcePackage)
        assertEquals("WhatsApp", capture.sourceAppLabel)
        assertEquals(0, capture.retryCount)
        assertEquals(java.time.Instant.ofEpochMilli(9_999L), capture.createdAt)
        assertEquals(java.time.Instant.ofEpochMilli(9_999L), capture.updatedAt)
        assertEquals(
            "notification:com.whatsapp:0|com.whatsapp|101|null|10001",
            capture.sourceRef
        )
    }
}
