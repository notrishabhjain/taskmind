package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.model.NotificationCapture

sealed interface CaptureProcessingResult {
    /**
     * The capture was handled and reached a terminal processing state.
     * [resultingTaskId] links the capture to the created task, or to the
     * pre-existing task when the intake funnel detected a duplicate.
     */
    data class Processed(val resultingTaskId: Long? = null) : CaptureProcessingResult

    data object ReviewRequired : CaptureProcessingResult

    data object Rejected : CaptureProcessingResult

    /** Extraction capability is not available yet; the capture waits in DEFERRED. */
    data class Deferred(val reason: String) : CaptureProcessingResult

    data class RetryableFailure(val reason: String?) : CaptureProcessingResult

    data class PermanentFailure(val reason: String?) : CaptureProcessingResult
}

interface NotificationCaptureProcessor {
    suspend fun process(capture: NotificationCapture): CaptureProcessingResult
}
