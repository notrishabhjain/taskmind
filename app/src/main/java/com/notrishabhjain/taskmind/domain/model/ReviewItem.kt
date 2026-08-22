package com.notrishabhjain.taskmind.domain.model

enum class ReviewStatus { PENDING, ACCEPTED, DISMISSED }

data class ReviewItem(
    val id: Long = 0L,
    val displayTitle: String,
    val titleKey: String,
    val notes: String? = null,
    val dueAt: Instant? = null,
    val priority: Priority = Priority.MEDIUM,
    val projectId: Long? = null,
    val sourceType: SourceType,
    val sourceRef: String,
    val sourceLabel: String? = null,
    val sourceApp: String? = null,
    val sourceText: String? = null,
    val evidence: String? = null,
    val reasoning: String? = null,
    val confidence: Double?,
    val status: ReviewStatus = ReviewStatus.PENDING,
    val resultingTaskId: Long? = null,
    val createdAt: Instant,
    val decidedAt: Instant? = null
)
