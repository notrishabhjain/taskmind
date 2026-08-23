package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.intake.IntakeOutcome
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.intake.TaskProposal
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.repository.ReviewRepository
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.time.Instant

sealed interface ReviewDecisionResult {
    data class Accepted(val task: Task) : ReviewDecisionResult

    data object Dismissed : ReviewDecisionResult

    data object DuplicateOfExistingTask : ReviewDecisionResult

    data object AlreadyDecided : ReviewDecisionResult

    data object Missing : ReviewDecisionResult

    data class Failed(val reason: String) : ReviewDecisionResult
}

class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val timeProvider: TimeProvider,
    private val taskIntakeService: TaskIntakeService
) {

    suspend fun accept(reviewItemId: Long): ReviewDecisionResult {
        val item = reviewRepository.findById(reviewItemId) ?: return ReviewDecisionResult.Missing
        if (item.status != ReviewStatus.PENDING) return ReviewDecisionResult.AlreadyDecided

        return when (val outcome = taskIntakeService.submit(TaskProposal.fromAcceptedReview(item))) {
            is IntakeOutcome.Created -> {
                activityLogRepository.append(
                    entry(
                        category = ActivityCategory.INTAKE_ACCEPTED,
                        message = "Confirmed \"${outcome.task.title}\"",
                        detail = "Review #$reviewItemId accepted; provenance preserved from original source",
                        taskId = outcome.task.id,
                        at = timeProvider.now()
                    )
                )
                ReviewDecisionResult.Accepted(outcome.task)
            }

            is IntakeOutcome.DuplicateDetected -> ReviewDecisionResult.DuplicateOfExistingTask

            is IntakeOutcome.Failed -> ReviewDecisionResult.Failed(outcome.reason)

            is IntakeOutcome.Rejected -> ReviewDecisionResult.Failed(outcome.reason.name.lowercase())

            is IntakeOutcome.RoutedToReview -> ReviewDecisionResult.Failed("routed back to review")
        }
    }

    suspend fun dismiss(reviewItemId: Long): ReviewDecisionResult {
        val item = reviewRepository.findById(reviewItemId) ?: return ReviewDecisionResult.Missing
        if (item.status != ReviewStatus.PENDING) return ReviewDecisionResult.AlreadyDecided

        val now = timeProvider.now()
        reviewRepository.markDecided(item.id, ReviewStatus.DISMISSED, null, now)
        activityLogRepository.append(
            entry(
                category = ActivityCategory.INTAKE_REJECTED,
                message = "Dismissed \"${item.displayTitle}\"",
                detail = confidenceDetail(item.confidence),
                taskId = null,
                at = now
            )
        )
        return ReviewDecisionResult.Dismissed
    }

    private fun entry(
        category: ActivityCategory,
        message: String,
        detail: String?,
        taskId: Long?,
        at: Instant
    ): ActivityLogEntry = ActivityLogEntry(
        category = category,
        message = message,
        detail = detail,
        taskId = taskId,
        createdAt = at
    )

    private fun confidenceDetail(confidence: Double?): String =
        if (confidence == null) "confidence missing" else "confidence=$confidence"
}
