package com.notrishabhjain.taskmind.domain.intake

import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.Task

sealed interface IntakeOutcome {
    data class Created(val task: Task) : IntakeOutcome

    data class RoutedToReview(val reviewItem: ReviewItem) : IntakeOutcome

    data class DuplicateDetected(val existingTaskId: Long, val titleKey: String) : IntakeOutcome

    data class Rejected(val reason: RejectionReason) : IntakeOutcome

    data class Failed(val reason: String) : IntakeOutcome
}

enum class RejectionReason {
    BLANK_TITLE,
    MISSING_SOURCE_REF,
    BELOW_CONFIDENCE_THRESHOLD,
    EVIDENCE_INVALID
}
