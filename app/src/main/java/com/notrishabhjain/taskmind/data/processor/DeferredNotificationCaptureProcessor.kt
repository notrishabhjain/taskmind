package com.notrishabhjain.taskmind.data.processor

import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import com.notrishabhjain.taskmind.domain.service.CaptureProcessingResult
import com.notrishabhjain.taskmind.domain.service.NotificationCaptureProcessor

/**
 * Milestone 4D placeholder processor.
 *
 * It deliberately performs no work and reports [CaptureProcessingResult.Deferred]
 * so that captures durably wait in the DEFERRED state until Milestone 4E binds a
 * real extraction processor. No Task/Review is created and nothing is discarded.
 */
class DeferredNotificationCaptureProcessor : NotificationCaptureProcessor {

    override suspend fun process(capture: NotificationCapture): CaptureProcessingResult =
        CaptureProcessingResult.Deferred(
            reason = "Notification processing is deferred until an extraction processor is bound (Milestone 4E)"
        )
}
