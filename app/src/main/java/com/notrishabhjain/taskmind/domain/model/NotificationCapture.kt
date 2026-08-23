package com.notrishabhjain.taskmind.domain.model

import java.time.Instant

enum class CaptureState {
    CAPTURED,
    QUEUED,
    PROCESSING,
    PROCESSED,
    REVIEWED,
    REJECTED,
    FAILED,
    RETRY_PENDING,
    IGNORED
}

object NotificationCaptureStateMachine {

    val TERMINAL_STATES = setOf(
        CaptureState.PROCESSED,
        CaptureState.REVIEWED,
        CaptureState.REJECTED,
        CaptureState.IGNORED,
        CaptureState.FAILED
    )

    private val ALLOWED_TRANSITIONS = mapOf(
        CaptureState.CAPTURED to setOf(CaptureState.QUEUED, CaptureState.IGNORED),
        CaptureState.QUEUED to setOf(CaptureState.PROCESSING),
        CaptureState.PROCESSING to setOf(
            CaptureState.PROCESSED,
            CaptureState.REVIEWED,
            CaptureState.REJECTED,
            CaptureState.RETRY_PENDING,
            CaptureState.FAILED
        ),
        CaptureState.RETRY_PENDING to setOf(CaptureState.PROCESSING),
        CaptureState.PROCESSED to emptySet(),
        CaptureState.REVIEWED to emptySet(),
        CaptureState.REJECTED to emptySet(),
        CaptureState.FAILED to emptySet(),
        CaptureState.IGNORED to emptySet()
    )

    fun canTransition(from: CaptureState, to: CaptureState): Boolean =
        to in ALLOWED_TRANSITIONS.getValue(from)

    fun isTerminal(state: CaptureState): Boolean = state in TERMINAL_STATES

    fun requireValidTransition(from: CaptureState, to: CaptureState) {
        check(canTransition(from, to)) {
            "Illegal capture transition ${from.name} -> ${to.name}"
        }
    }
}

data class NotificationCapture(
    val id: Long = 0L,
    val idempotencyKey: String,
    val sourcePackage: String,
    val sourceAppLabel: String? = null,
    val notificationKey: String,
    val notificationId: Int? = null,
    val notificationTag: String? = null,
    val postTime: Instant,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val infoText: String? = null,
    val conversationTitle: String? = null,
    val category: String? = null,
    val channelLabel: String? = null,
    val canonicalSourceText: String,
    val contentHash: String,
    val sourceRef: String,
    val state: CaptureState = CaptureState.CAPTURED,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val resultingTaskId: Long? = null,
    val processedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
