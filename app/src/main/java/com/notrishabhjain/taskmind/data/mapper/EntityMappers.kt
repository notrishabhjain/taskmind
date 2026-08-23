package com.notrishabhjain.taskmind.data.mapper

import com.notrishabhjain.taskmind.data.db.entity.ActivityLogEntity
import com.notrishabhjain.taskmind.data.db.entity.NotificationCaptureEntity
import com.notrishabhjain.taskmind.data.db.entity.ProjectEntity
import com.notrishabhjain.taskmind.data.db.entity.ReviewItemEntity
import com.notrishabhjain.taskmind.data.db.entity.TagEntity
import com.notrishabhjain.taskmind.data.db.entity.TaskEntity
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.InferenceOrigin
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.Project
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.SyncState
import com.notrishabhjain.taskmind.domain.model.Tag
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskStatus
import java.time.Instant

internal fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    titleKey = titleKey,
    notes = notes,
    dueAt = dueAt?.toEpochMilli(),
    priority = priority.name,
    status = status.name,
    projectId = projectId,
    recurrenceRule = recurrenceRule,
    reminderAt = reminderAt?.toEpochMilli(),
    parentTaskId = parentTaskId,
    sortOrder = sortOrder,
    sourceType = sourceType.name,
    sourceRef = sourceRef,
    sourceLabel = sourceLabel,
    sourceApp = sourceApp,
    evidence = evidence,
    confidence = confidence,
    inferenceOrigin = inferenceOrigin?.name,
    modelId = modelId,
    remoteId = remoteId,
    syncState = syncState.name,
    completedAt = completedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)

internal fun TaskEntity.toDomain(tagIds: List<Long> = emptyList()): Task = Task(
    id = id,
    title = title,
    titleKey = titleKey,
    notes = notes,
    dueAt = dueAt?.let(Instant::ofEpochMilli),
    priority = parseEnum(priority, "Priority"),
    status = parseEnum(status, "TaskStatus"),
    projectId = projectId,
    tagIds = tagIds,
    recurrenceRule = recurrenceRule,
    reminderAt = reminderAt?.let(Instant::ofEpochMilli),
    parentTaskId = parentTaskId,
    sortOrder = sortOrder,
    sourceType = parseEnum(sourceType, "SourceType"),
    sourceRef = sourceRef,
    sourceLabel = sourceLabel,
    sourceApp = sourceApp,
    evidence = evidence,
    confidence = confidence,
    inferenceOrigin = inferenceOrigin?.let { parseEnum(it, "InferenceOrigin") },
    modelId = modelId,
    remoteId = remoteId,
    syncState = parseEnum(syncState, "SyncState"),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

internal fun ReviewItem.toEntity(): ReviewItemEntity = ReviewItemEntity(
    id = id,
    displayTitle = displayTitle,
    titleKey = titleKey,
    notes = notes,
    dueAt = dueAt?.toEpochMilli(),
    priority = priority.name,
    projectId = projectId,
    sourceType = sourceType.name,
    sourceRef = sourceRef,
    sourceLabel = sourceLabel,
    sourceApp = sourceApp,
    sourceText = sourceText,
    evidence = evidence,
    reasoning = reasoning,
    confidence = confidence,
    status = status.name,
    resultingTaskId = resultingTaskId,
    createdAt = createdAt.toEpochMilli(),
    decidedAt = decidedAt?.toEpochMilli()
)

internal fun ReviewItemEntity.toDomain(): ReviewItem = ReviewItem(
    id = id,
    displayTitle = displayTitle,
    titleKey = titleKey,
    notes = notes,
    dueAt = dueAt?.let(Instant::ofEpochMilli),
    priority = parseEnum(priority, "Priority"),
    projectId = projectId,
    sourceType = parseEnum(sourceType, "SourceType"),
    sourceRef = sourceRef,
    sourceLabel = sourceLabel,
    sourceApp = sourceApp,
    sourceText = sourceText,
    evidence = evidence,
    reasoning = reasoning,
    confidence = confidence,
    status = parseEnum(status, "ReviewStatus"),
    resultingTaskId = resultingTaskId,
    createdAt = Instant.ofEpochMilli(createdAt),
    decidedAt = decidedAt?.let(Instant::ofEpochMilli)
)

internal fun ActivityLogEntry.toEntity(): ActivityLogEntity = ActivityLogEntity(
    id = id,
    category = category.name,
    message = message,
    detail = detail,
    taskId = taskId,
    createdAt = createdAt.toEpochMilli()
)

internal fun ActivityLogEntity.toDomain(): ActivityLogEntry = ActivityLogEntry(
    id = id,
    category = parseEnum(category, "ActivityCategory"),
    message = message,
    detail = detail,
    taskId = taskId,
    createdAt = Instant.ofEpochMilli(createdAt)
)

internal fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    nameKey = nameKey
)

internal fun NotificationCapture.toEntity(): NotificationCaptureEntity = NotificationCaptureEntity(
    id = id,
    idempotencyKey = idempotencyKey,
    sourcePackage = sourcePackage,
    sourceAppLabel = sourceAppLabel,
    notificationKey = notificationKey,
    notificationId = notificationId,
    notificationTag = notificationTag,
    postTime = postTime.toEpochMilli(),
    title = title,
    text = text,
    bigText = bigText,
    subText = subText,
    infoText = infoText,
    conversationTitle = conversationTitle,
    category = category,
    channelLabel = channelLabel,
    canonicalSourceText = canonicalSourceText,
    contentHash = contentHash,
    sourceRef = sourceRef,
    state = state.name,
    retryCount = retryCount,
    lastError = lastError,
    resultingTaskId = resultingTaskId,
    processedAt = processedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)

internal fun NotificationCaptureEntity.toDomain(): NotificationCapture = NotificationCapture(
    id = id,
    idempotencyKey = idempotencyKey,
    sourcePackage = sourcePackage,
    sourceAppLabel = sourceAppLabel,
    notificationKey = notificationKey,
    notificationId = notificationId,
    notificationTag = notificationTag,
    postTime = Instant.ofEpochMilli(postTime),
    title = title,
    text = text,
    bigText = bigText,
    subText = subText,
    infoText = infoText,
    conversationTitle = conversationTitle,
    category = category,
    channelLabel = channelLabel,
    canonicalSourceText = canonicalSourceText,
    contentHash = contentHash,
    sourceRef = sourceRef,
    state = parseEnum(state, "CaptureState"),
    retryCount = retryCount,
    lastError = lastError,
    resultingTaskId = resultingTaskId,
    processedAt = processedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

internal fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    nameKey = nameKey
)

private inline fun <reified T : Enum<T>> parseEnum(raw: String, typeName: String): T =
    enumValues<T>().firstOrNull { it.name == raw }
        ?: throw IllegalStateException("Unknown $typeName value stored in database: $raw")
