package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.model.NotificationCapture

sealed interface CaptureProcessingResult {
    data object Processed : CaptureProcessingResult

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
