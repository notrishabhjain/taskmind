package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.time.Instant
import java.time.ZoneId

/**
 * Deterministic, explainable result of extracting a task candidate from a
 * captured notification. All fields are derived from the capture; the original
 * canonical text is never modified.
 */
data class NotificationExtraction(
    val title: String,
    val notes: String?,
    val dueAt: Instant?,
    /** Deterministic heuristic score in [0.0, 1.0]; routing input only. */
    val confidence: Double,
    /** Verbatim span of the capture's canonical source text backing the title. */
    val evidence: String,
    /** Concise human-readable explanation of what was detected. */
    val reasoning: String,
    val modelId: String
)

sealed interface NotificationExtractionOutcome {
    data class Actionable(val extraction: NotificationExtraction) : NotificationExtractionOutcome

    /** Clearly informational content (OTP, marketing, status updates, ...). */
    data class NotActionable(val reason: String) : NotificationExtractionOutcome
}

/**
 * Extraction seam for notification processing. The current implementation is
 * deterministic and local ([DeterministicNotificationTaskExtractor]); a future
 * on-device model can replace it without touching the processor or funnel.
 */
fun interface NotificationTaskExtractor {
    fun extract(capture: NotificationCapture, now: Instant, zone: ZoneId): NotificationExtractionOutcome
}
