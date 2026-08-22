package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.intake.TitleNormalizer
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskStatus
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.repository.ProjectTagRepository
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.time.Instant

class TaskService(
    private val taskRepository: TaskRepository,
    private val projectTagRepository: ProjectTagRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val timeProvider: TimeProvider
) {

    suspend fun complete(taskId: Long): Boolean = transition(
        taskId = taskId,
        allowedFrom = setOf(TaskStatus.ACTIVE),
        category = ActivityCategory.TASK_COMPLETED,
        message = { "Task \"$it\" marked complete" }
    ) { task, now ->
        task.copy(status = TaskStatus.COMPLETED, completedAt = now)
    }

    suspend fun reopen(taskId: Long): Boolean = transition(
        taskId = taskId,
        allowedFrom = setOf(TaskStatus.COMPLETED),
        category = ActivityCategory.TASK_REOPENED,
        message = { "Task \"$it\" reopened" }
    ) { task, _ ->
        task.copy(status = TaskStatus.ACTIVE, completedAt = null)
    }

    suspend fun archive(taskId: Long): Boolean = transition(
        taskId = taskId,
        allowedFrom = setOf(TaskStatus.ACTIVE, TaskStatus.COMPLETED),
        category = ActivityCategory.TASK_ARCHIVED,
        message = { "Task \"$it\" archived" }
    ) { task, _ ->
        task.copy(status = TaskStatus.ARCHIVED)
    }

    suspend fun unarchive(taskId: Long): Boolean = transition(
        taskId = taskId,
        allowedFrom = setOf(TaskStatus.ARCHIVED),
        category = ActivityCategory.TASK_UNARCHIVED,
        message = { "Task \"$it\" restored from archive" }
    ) { task, _ ->
        task.copy(status = TaskStatus.ACTIVE)
    }

    suspend fun delete(taskId: Long): Boolean = transition(
        taskId = taskId,
        allowedFrom = setOf(TaskStatus.ACTIVE, TaskStatus.COMPLETED, TaskStatus.ARCHIVED),
        category = ActivityCategory.TASK_DELETED,
        message = { "Task \"$it\" deleted" },
        detail = { "Soft-deleted; recoverable while no purge policy has run" }
    ) { task, _ ->
        task.copy(status = TaskStatus.DELETED)
    }

    suspend fun update(taskId: Long, edit: TaskEdit): Boolean {
        val current = taskRepository.findById(taskId) ?: return false

        val displayTitle = TitleNormalizer.normalize(edit.title)
        if (displayTitle.isBlank()) return false

        val now = timeProvider.now()
        val updated = current.copy(
            title = displayTitle,
            titleKey = TitleNormalizer.titleKey(edit.title),
            notes = edit.notes?.trim()?.ifBlank { null },
            priority = edit.priority,
            dueAt = edit.dueAt,
            updatedAt = now
        )

        taskRepository.update(updated)
        activityLogRepository.append(
            entry(
                category = ActivityCategory.TASK_UPDATED,
                message = "Task \"${updated.title}\" updated",
                detail = null,
                taskId = updated.id,
                at = now
            )
        )
        return true
    }

    private suspend fun transition(
        taskId: Long,
        allowedFrom: Set<TaskStatus>,
        category: ActivityCategory,
        message: (String) -> String,
        detail: ((Task) -> String)? = null,
        transform: (Task, Instant) -> Task
    ): Boolean {
        val current = taskRepository.findById(taskId) ?: return false
        if (current.status !in allowedFrom) return false

        val now = timeProvider.now()
        val updated = transform(current, now).copy(updatedAt = now)

        taskRepository.update(updated)
        activityLogRepository.append(
            entry(
                category = category,
                message = message(updated.title),
                detail = detail?.invoke(current)
                    ?.plus(" (was ${current.status.name.lowercase()})"),
                taskId = updated.id,
                at = now
            )
        )
        return true
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
}
