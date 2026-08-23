package com.notrishabhjain.taskmind.ui.activitylog

import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository

data class ActivityRowUi(
    val id: Long,
    val timestampLabel: String,
    val categoryLabelRes: Int,
    val message: String,
    val detail: String?,
    val taskId: Long?
)

data class ActivityLogUiState(
    val loading: Boolean = true,
    val rows: List<ActivityRowUi> = emptyList()
) {
    val isEmpty: Boolean get() = !loading && rows.isEmpty()

    companion object {
        const val DISPLAY_LIMIT = ActivityLogRepository.RECENT_DISPLAY_LIMIT
    }
}

fun categoryLabelRes(category: ActivityCategory): Int = when (category) {
    ActivityCategory.TASK_CREATED -> R.string.category_task_created
    ActivityCategory.TASK_UPDATED -> R.string.category_task_updated
    ActivityCategory.TASK_COMPLETED -> R.string.category_task_completed
    ActivityCategory.TASK_REOPENED -> R.string.category_task_reopened
    ActivityCategory.TASK_ARCHIVED -> R.string.category_task_archived
    ActivityCategory.TASK_UNARCHIVED -> R.string.category_task_unarchived
    ActivityCategory.TASK_DELETED -> R.string.category_task_deleted
    ActivityCategory.INTAKE_ACCEPTED -> R.string.category_intake_accepted
    ActivityCategory.INTAKE_REJECTED -> R.string.category_intake_rejected
    ActivityCategory.INTAKE_SENT_TO_REVIEW -> R.string.category_intake_sent_to_review
    ActivityCategory.DUPLICATE_DETECTED -> R.string.category_duplicate_detected
    ActivityCategory.EVIDENCE_VALIDATION_FAILED -> R.string.category_evidence_failed
    ActivityCategory.PROCESSING_FAILED -> R.string.category_processing_failed
    ActivityCategory.RETRY_SCHEDULED -> R.string.category_retry_scheduled
    ActivityCategory.CAPTURE_IGNORED -> R.string.category_capture_ignored
    ActivityCategory.CAPTURE_DUPLICATE -> R.string.category_capture_duplicate
    ActivityCategory.CAPTURE_VERSIONED -> R.string.category_capture_versioned
}
