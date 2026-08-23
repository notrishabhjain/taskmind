package com.notrishabhjain.taskmind.domain.model

import java.time.Instant

enum class ActivityCategory {
    TASK_CREATED,
    TASK_UPDATED,
    TASK_COMPLETED,
    TASK_REOPENED,
    TASK_ARCHIVED,
    TASK_UNARCHIVED,
    TASK_DELETED,
    INTAKE_ACCEPTED,
    INTAKE_REJECTED,
    INTAKE_SENT_TO_REVIEW,
    DUPLICATE_DETECTED,
    EVIDENCE_VALIDATION_FAILED,
    PROCESSING_FAILED,
    RETRY_SCHEDULED,
    CAPTURE_IGNORED,
    CAPTURE_DUPLICATE,
    CAPTURE_VERSIONED,
    CAPTURE_QUEUED,
    CAPTURE_DEFERRED,
    CAPTURE_PROCESSED
}

data class ActivityLogEntry(
    val id: Long = 0L,
    val category: ActivityCategory,
    val message: String,
    val detail: String? = null,
    val taskId: Long? = null,
    val createdAt: Instant
)

object ActivityLogPolicy {
    const val RETENTION_LIMIT = 300
}
