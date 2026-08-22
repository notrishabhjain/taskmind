package com.notrishabhjain.taskmind.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SimpleSQLiteQuery
import com.notrishabhjain.taskmind.data.db.dao.TaskDao
import com.notrishabhjain.taskmind.data.db.entity.TaskTagCrossRef
import com.notrishabhjain.taskmind.data.mapper.toDomain
import com.notrishabhjain.taskmind.data.mapper.toEntity
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskQuery
import com.notrishabhjain.taskmind.domain.model.TaskSort
import com.notrishabhjain.taskmind.domain.model.TaskView
import com.notrishabhjain.taskmind.domain.repository.DuplicateTaskException
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomTaskRepository(
    private val taskDao: TaskDao
) : TaskRepository {

    override suspend fun insert(task: Task): Task {
        val newId = try {
            taskDao.insert(task.toEntity())
        } catch (e: SQLiteConstraintException) {
            val sourceRef = task.sourceRef
                ?: throw IllegalStateException(
                    "Constraint failure while inserting manual task \"${task.titleKey}\"",
                    e
                )
            throw DuplicateTaskException(task.sourceType, sourceRef, task.titleKey)
        }
        linkTags(newId, task.tagIds)
        return requireNotNull(loadTask(newId)) { "Inserted task disappeared: $newId" }
    }

    override suspend fun update(task: Task) {
        taskDao.update(task.toEntity())
        taskDao.clearTaskTags(task.id)
        linkTags(task.id, task.tagIds)
    }

    override suspend fun findById(id: Long): Task? = loadTask(id)

    override suspend fun findByLogicalKey(
        sourceType: SourceType,
        sourceRef: String,
        titleKey: String
    ): Task? {
        val entity = taskDao.findByLogicalKey(
            sourceType = sourceType.name,
            sourceRef = sourceRef,
            titleKey = titleKey
        ) ?: return null
        return entity.toDomain(tagIdsFor(entity.id))
    }

    private suspend fun loadTask(id: Long): Task? {
        val entity = taskDao.findById(id) ?: return null
        return entity.toDomain(tagIdsFor(id))
    }

    override fun observeTasks(
        query: TaskQuery,
        dayStart: Instant,
        dayEnd: Instant
    ): Flow<List<Task>> {
        val sql = StringBuilder("SELECT * FROM tasks WHERE ")
        val args = mutableListOf<Any>()

        when (query.view) {
            TaskView.TODAY -> {
                sql.append("status = 'ACTIVE' AND dueAt >= ? AND dueAt < ?")
                args += dayStart.toEpochMilli()
                args += dayEnd.toEpochMilli()
            }

            TaskView.UPCOMING -> {
                sql.append("status = 'ACTIVE' AND dueAt >= ?")
                args += dayEnd.toEpochMilli()
            }

            TaskView.OVERDUE -> {
                sql.append("status = 'ACTIVE' AND dueAt IS NOT NULL AND dueAt < ?")
                args += dayStart.toEpochMilli()
            }

            TaskView.COMPLETED -> sql.append("status = 'COMPLETED'")
            TaskView.ARCHIVED -> sql.append("status = 'ARCHIVED'")
            TaskView.ALL -> sql.append("status IN ('ACTIVE', 'COMPLETED')")
        }

        query.search.trim().takeIf { it.isNotEmpty() }?.let { term ->
            sql.append(" AND (title LIKE ? ESCAPE '\\' OR notes LIKE ? ESCAPE '\\')")
            val pattern = "%" + escapeLike(term) + "%"
            args += pattern
            args += pattern
        }

        sql.append(orderByClause(query))

        return taskDao.observeTasks(SimpleSQLiteQuery(sql.toString(), args.toTypedArray()))
            .map { entities -> entities.map { entity -> entity.toDomain(tagIdsFor(entity.id)) } }
    }

    private suspend fun tagIdsFor(taskId: Long): List<Long> =
        taskDao.tagIdsForTask(taskId)

    private suspend fun linkTags(taskId: Long, tagIds: List<Long>) {
        if (tagIds.isNotEmpty()) {
            taskDao.insertTaskTags(tagIds.map { TaskTagCrossRef(taskId = taskId, tagId = it) })
        }
    }

    private fun orderByClause(query: TaskQuery): String {
        if (query.view == TaskView.COMPLETED) return " ORDER BY completedAt DESC, id DESC"
        if (query.view == TaskView.ARCHIVED) return " ORDER BY updatedAt DESC, id DESC"

        val priorityRank =
            "CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END"
        return when (query.sort) {
            TaskSort.DUE_DATE ->
                " ORDER BY (dueAt IS NULL), dueAt ASC, $priorityRank ASC, createdAt DESC, id DESC"

            TaskSort.PRIORITY ->
                " ORDER BY $priorityRank ASC, (dueAt IS NULL), dueAt ASC, createdAt DESC, id DESC"

            TaskSort.CREATED ->
                " ORDER BY createdAt DESC, id DESC"
        }
    }

    private fun escapeLike(term: String): String = term
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
