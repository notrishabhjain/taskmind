package com.notrishabhjain.taskmind.data.processor

import com.notrishabhjain.taskmind.domain.intake.IntakeOutcome
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.intake.TaskProposal
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.service.CaptureProcessingResult
import com.notrishabhjain.taskmind.domain.service.NotificationCaptureProcessor
import com.notrishabhjain.taskmind.domain.service.NotificationExtractionOutcome
import com.notrishabhjain.taskmind.domain.service.NotificationTaskExtractor
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.time.ZoneId

/**
 * Milestone 4E processing pipeline: deterministic extraction followed by the
 * single task-intake funnel.
 *
 * Routing contract:
 * - Not-actionable notification            -> Rejected (capture REJECTED)
 * - Intake Created                          -> Processed(resultingTaskId)
 * - Intake RoutedToReview (ConfidenceGate)  -> ReviewRequired (capture REVIEWED)
 * - Intake DuplicateDetected                -> Processed(existing task id);
 *                                              the funnel stays authoritative,
 *                                              no second task is created
 * - Intake Rejected                         -> Rejected
 * - Intake Failed                           -> RetryableFailure (4D retry policy)
 *
 * This class never writes to the Task table directly and never logs
 * notification content; all persistence and logging flows through
 * TaskIntakeService and CaptureProcessingCoordinator.
 */
class NotificationTaskProcessor(
    private val extractor: NotificationTaskExtractor,
    private val taskIntakeService: TaskIntakeService,
    private val timeProvider: TimeProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : NotificationCaptureProcessor {

    override suspend fun process(capture: NotificationCapture): CaptureProcessingResult {
        val extraction = when (val outcome = extractor.extract(capture, timeProvider.now(), zoneId)) {
            is NotificationExtractionOutcome.Actionable -> outcome.extraction
            is NotificationExtractionOutcome.NotActionable -> return CaptureProcessingResult.Rejected
        }

        val proposal = TaskProposal.extracted(
            sourceType = SourceType.NOTIFICATION,
            sourceRef = capture.sourceRef,
            title = extraction.title,
            confidence = extraction.confidence,
            evidence = extraction.evidence,
            sourceText = capture.canonicalSourceText,
            notes = extraction.notes,
            dueAt = extraction.dueAt,
            sourceLabel = capture.sourceAppLabel,
            sourceApp = capture.sourcePackage,
            reasoning = extraction.reasoning,
            modelId = extraction.modelId
        )

        return when (val outcome = taskIntakeService.submit(proposal)) {
            is IntakeOutcome.Created -> CaptureProcessingResult.Processed(resultingTaskId = outcome.task.id)
            is IntakeOutcome.RoutedToReview -> CaptureProcessingResult.ReviewRequired
            is IntakeOutcome.DuplicateDetected -> CaptureProcessingResult.Processed(resultingTaskId = outcome.existingTaskId)
            is IntakeOutcome.Rejected -> CaptureProcessingResult.Rejected
            is IntakeOutcome.Failed -> CaptureProcessingResult.RetryableFailure(outcome.reason)
        }
    }
}
