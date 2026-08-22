package com.notrishabhjain.taskmind.domain.model

import java.time.Instant

enum class Priority { URGENT, HIGH, MEDIUM, LOW }

enum class TaskStatus { ACTIVE, COMPLETED, ARCHIVED, DELETED }

enum class SourceType { NOTIFICATION, CALL, MANUAL, REVIEW }

enum class SyncState { LOCAL_ONLY, PENDING_SYNC, SYNCED }

enum class InferenceOrigin { HUMAN_INPUT, AUTOMATIC_INFERENCE, REVIEW_CONFIRMATION }

data class Task(
    val id: Long = 0L,
    val title: String,
    val titleKey: String,
    val notes: String? = null,
    val dueAt: Instant? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val projectId: Long? = null,
    val tagIds: List<Long> = emptyList(),
    val recurrenceRule: String? = null,
    val reminderAt: Instant? = null,
    val parentTaskId: Long? = null,
    val sortOrder: Int? = null,
    val sourceType: SourceType = SourceType.MANUAL,
    val sourceRef: String? = null,
    val sourceLabel: String? = null,
    val sourceApp: String? = null,
    val evidence: String? = null,
    val confidence: Double? = null,
    val inferenceOrigin: InferenceOrigin? = null,
    val modelId: String? = null,
    val remoteId: String? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
