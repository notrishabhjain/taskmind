package com.notrishabhjain.taskmind.domain.intake

import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskQuery
import com.notrishabhjain.taskmind.domain.model.TaskStatus
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.repository.ProjectTagRepository
import com.notrishabhjain.taskmind.domain.repository.ReviewRepository
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FixedTimeProvider(start: Long = 0L) : TimeProvider {

    private var current: Long = start

    override fun now() = java.time.Instant.ofEpochMilli(current)

    fun advanceBy(millis: Long) {
        current += millis
    }
}

class FakeTaskRepository : TaskRepository {

    data class Observation(
        val query: TaskQuery,
        val dayStartMillis: Long,
        val dayEndMillis: Long
    )

    private val tasksById = LinkedHashMap<Long, Task>()
    private var nextId = 1L

    val all: List<Task> get() = tasksById.values.toList()
    val size: Int get() = tasksById.size
    val observations = mutableListOf<Observation>()

    override suspend fun insert(task: Task): Task {
        val stored = task.copy(id = nextId++)
        tasksById[stored.id] = stored
        return stored
    }

    override suspend fun update(task: Task) {
        require(tasksById.containsKey(task.id)) { "No task ${task.id} to update" }
        tasksById[task.id] = task
    }

    override suspend fun findById(id: Long): Task? = tasksById[id]

    override suspend fun findByLogicalKey(
        sourceType: SourceType,
        sourceRef: String,
        titleKey: String
    ): Task? = tasksById.values.firstOrNull {
        it.sourceType == sourceType && it.sourceRef == sourceRef && it.titleKey == titleKey
    }

    override fun observeTasks(
        query: TaskQuery,
        dayStart: Instant,
        dayEnd: Instant
    ): Flow<List<Task>> = flow {
        observations += Observation(query, dayStart.toEpochMilli(), dayEnd.toEpochMilli())
        emit(tasksById.values.filter { it.status != TaskStatus.DELETED }.sortedBy { it.id })
    }
}

class FakeReviewRepository : ReviewRepository {

    private val itemsById = LinkedHashMap<Long, ReviewItem>()
    private var nextId = 1L

    val all: List<ReviewItem> get() = itemsById.values.toList()

    override suspend fun insert(item: ReviewItem): ReviewItem {
        val stored = item.copy(id = nextId++)
        itemsById[stored.id] = stored
        return stored
    }

    override suspend fun findById(id: Long): ReviewItem? = itemsById[id]

    override fun observePending(): Flow<List<ReviewItem>> = flow {
        emit(
            itemsById.values
                .filter { it.status == ReviewStatus.PENDING }
                .sortedByDescending { it.createdAt.toEpochMilli() }
        )
    }

    override suspend fun markDecided(
        id: Long,
        status: ReviewStatus,
        resultingTaskId: Long?,
        decidedAt: Instant
    ) {
        val existing = itemsById.getValue(id)
        itemsById[id] = existing.copy(
            status = status,
            resultingTaskId = resultingTaskId,
            decidedAt = decidedAt
        )
    }
}

class FakeActivityLogRepository : ActivityLogRepository {

    val entries = mutableListOf<ActivityLogEntry>()

    fun countOf(category: ActivityCategory): Int =
        entries.count { it.category == category }

    override suspend fun append(entry: ActivityLogEntry) {
        entries += entry
    }

    override fun observeRecent(limit: Int): Flow<List<ActivityLogEntry>> = flow {
        emit(entries.sortedByDescending { it.createdAt.toEpochMilli() }.take(limit))
    }
}

class FakeProjectTagRepository : ProjectTagRepository {

    private val idsByNameKey = LinkedHashMap<String, Long>()
    private var nextId = 1L

    override suspend fun ensureTags(names: List<String>): List<Long> = names
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .map { idsByNameKey.getOrPut(it) { nextId++ } }

    override suspend fun ensureProject(name: String): Long {
        val key = name.trim().lowercase()
        if (key.isBlank()) return 0L
        return idsByNameKey.getOrPut("project:$key") { nextId++ }
    }
}
