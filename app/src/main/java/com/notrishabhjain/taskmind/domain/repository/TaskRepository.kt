package com.notrishabhjain.taskmind.domain.repository

import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskQuery
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TaskRepository {

    suspend fun insert(task: Task): Task

    suspend fun update(task: Task)

    suspend fun findById(id: Long): Task?

    suspend fun findByLogicalKey(sourceType: SourceType, sourceRef: String, titleKey: String): Task?

    fun observeTasks(query: TaskQuery, dayStart: Instant, dayEnd: Instant): Flow<List<Task>>
}
