package com.notrishabhjain.taskmind.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureFilterTest {

    private val input = RawNotificationInput(
        sourcePackage = "com.whatsapp",
        notificationKey = "0|com.whatsapp|101|null|10001",
        postTimeMillis = 1_700_000_000_000L,
        title = "Dad",
        text = "Please buy milk tomorrow"
    )

    private fun filter(
        enabled: Boolean = true,
        selfPackage: String = "com.notrishabhjain.taskmind",
        allowed: Set<String> = emptySet(),
        blocked: Set<String> = emptySet(),
        minChars: Int = 3
    ) = CaptureFilter(
        NotificationCapturePolicy(
            captureEnabled = enabled,
            selfPackage = selfPackage,
            allowedPackages = allowed,
            blockedPackages = blocked,
            minimumContentChars = minChars
        )
    )

    @Test
    fun `enabled policy accepts a normal notification`() {
        assertEquals(CaptureDecision.ACCEPT, filter().decide(input))
    }

    @Test
    fun `disabled policy ignores everything`() {
        assertEquals(CaptureDecision.IGNORE, filter(enabled = false).decide(input))
    }

    @Test
    fun `self package is ignored even when enabled`() {
        val selfInput = input.copy(sourcePackage = "com.notrishabhjain.taskmind")

        assertEquals(
            CaptureDecision.IGNORE,
            filter(selfPackage = "com.notrishabhjain.taskmind").decide(selfInput)
        )
    }

    @Test
    fun `blocklisted package is ignored`() {
        assertEquals(
            CaptureDecision.IGNORE,
            filter(blocked = setOf("com.whatsapp")).decide(input)
        )
    }

    @Test
    fun `non-blocklisted package passes the blocklist`() {
        assertEquals(
            CaptureDecision.ACCEPT,
            filter(blocked = setOf("com.other.app")).decide(input)
        )
    }

    @Test
    fun `allowlisted package is accepted`() {
        assertEquals(
            CaptureDecision.ACCEPT,
            filter(allowed = setOf("com.whatsapp")).decide(input)
        )
    }

    @Test
    fun `package missing from non-empty allowlist is ignored`() {
        assertEquals(
            CaptureDecision.IGNORE,
            filter(allowed = setOf("com.telegram")).decide(input)
        )
    }

    @Test
    fun `blocklist wins over allowlist`() {
        val both = filter(allowed = setOf("com.whatsapp"), blocked = setOf("com.whatsapp"))

        assertEquals(CaptureDecision.IGNORE, both.decide(input))
    }

    @Test
    fun `content shorter than minimum is ignored`() {
        val tiny = input.copy(text = "ok", title = null)

        assertEquals(CaptureDecision.IGNORE, filter(minChars = 3).decide(tiny))
    }

    @Test
    fun `content meeting minimum length is accepted`() {
        val ok = input.copy(title = null, text = "okay!")

        assertEquals(CaptureDecision.ACCEPT, filter(minChars = 3).decide(ok))
    }

    @Test
    fun `decisions are deterministic for identical inputs`() {
        val first = filter().decide(input)
        repeat(5) { assertEquals(first, filter().decide(input)) }
    }
}
