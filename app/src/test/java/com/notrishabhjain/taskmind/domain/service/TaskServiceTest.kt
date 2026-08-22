package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.intake.FakeProjectTagRepository
import com.notrishabhjain.taskmind.domain.intake.FakeTaskRepository
import com.notrishabhjain.taskmind.domain.intake.FixedTimeProvider
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.InferenceOrigin
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskServiceTest {

    private lateinit var tasks: FakeTaskRepository
    private lateinit var activityLog: FakeActivityLogRepository
    private lateinit var service: TaskService

    private val seededAt: Instant = Instant.ofEpochMilli(1_000L)

    @Before
    fun setUp() {
        tasks = FakeTaskRepository()
        activityLog = FakeActivityLogRepository()
        service = TaskService(
            taskRepository = tasks,
            projectTagRepository = FakeProjectTagRepository(),
            activityLogRepository = activityLog,
            timeProvider = FixedTimeProvider(start = 5_000L)
        )
    }

    private suspend fun seedAutomatic(): Task = tasks.insert(
        Task(
            id = 0L,
            title = "Pay bill",
            titleKey = "pay bill",
            priority = Priority.MEDIUM,
            projectId = 3L,
            tagIds = listOf(5L, 6L),
            sourceType = SourceType.NOTIFICATION,
            sourceRef = "wa:77",
            sourceApp = "WhatsApp",
            evidence = "pay the bill",
            confidence = 0.9,
            inferenceOrigin = InferenceOrigin.AUTOMATIC_INFERENCE,
            modelId = "extractor-v1",
            createdAt = seededAt,
            updatedAt = seededAt
        )
    )

    @Test
    fun `complete marks completed with timestamps and logs`() = runBlocking {
        val task = seedAutomatic()

        val result = service.complete(task.id)

        assertTrue(result)
        val stored = tasks.findById(task.id)!!
        assertEquals(TaskStatus.COMPLETED, stored.status)
        assertEquals(5_000L, stored.completedAt!!.toEpochMilli())
        assertEquals(5_000L, stored.updatedAt.toEpochMilli())
        assertEquals(seededAt, stored.createdAt)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_COMPLETED))
        assertEquals(task.id, activityLog.entries.single { it.category == ActivityCategory.TASK_COMPLETED }.taskId)
    }

    @Test
    fun `complete fails on already completed task`() = runBlocking {
        val task = seedAutomatic()
        service.complete(task.id)

        val secondAttempt = service.complete(task.id)

        assertFalse(secondAttempt)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_COMPLETED))
    }

    @Test
    fun `reopen returns task to active and clears completion`() = runBlocking {
        val task = seedAutomatic()
        service.complete(task.id)

        val result = service.reopen(task.id)

        assertTrue(result)
        val stored = tasks.findById(task.id)!!
        assertEquals(TaskStatus.ACTIVE, stored.status)
        assertNull(stored.completedAt)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_REOPENED))
    }

    @Test
    fun `archive hides from active lifecycle and unarchive restores`() = runBlocking {
        val task = seedAutomatic()

        assertTrue(service.archive(task.id))
        assertEquals(TaskStatus.ARCHIVED, tasks.findById(task.id)!!.status)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_ARCHIVED))

        assertFalse(service.archive(task.id))

        assertTrue(service.unarchive(task.id))
        assertEquals(TaskStatus.ACTIVE, tasks.findById(task.id)!!.status)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_UNARCHIVED))
    }

    @Test
    fun `delete soft deletes and cannot delete twice`() = runBlocking {
        val task = seedAutomatic()

        assertTrue(service.delete(task.id))
        assertEquals(TaskStatus.DELETED, tasks.findById(task.id)!!.status)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_DELETED))

        assertFalse(service.delete(task.id))
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_DELETED))
    }

    @Test
    fun `update rewrites editable fields while preserving provenance`() = runBlocking {
        val task = seedAutomatic()

        val result = service.update(
            taskId = task.id,
            edit = TaskEdit(
                title = "  Pay   BILL today! ",
                notes = "  ",
                priority = Priority.HIGH,
                dueAt = Instant.ofEpochMilli(99_999L)
            )
        )

        assertTrue(result)
        val stored = tasks.findById(task.id)!!
        assertEquals("Pay BILL today!", stored.title)
        assertEquals("pay bill today", stored.titleKey)
        assertNull(stored.notes)
        assertEquals(Priority.HIGH, stored.priority)
        assertEquals(Instant.ofEpochMilli(99_999L), stored.dueAt)

        assertEquals("wa:77", stored.sourceRef)
        assertEquals("WhatsApp", stored.sourceApp)
        assertEquals("pay the bill", stored.evidence)
        assertEquals(0.9, stored.confidence!!, 0.0)
        assertEquals(InferenceOrigin.AUTOMATIC_INFERENCE, stored.inferenceOrigin)
        assertEquals("extractor-v1", stored.modelId)
        assertEquals(3L, stored.projectId)
        assertEquals(listOf(5L, 6L), stored.tagIds)
        assertEquals(seededAt, stored.createdAt)
        assertEquals(5_000L, stored.updatedAt.toEpochMilli())
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_UPDATED))
    }

    @Test
    fun `blank title update is rejected without touching the task`() = runBlocking {
        val task = seedAutomatic()

        val result = service.update(
            taskId = task.id,
            edit = TaskEdit(title = "   ", notes = null, priority = Priority.LOW, dueAt = null)
        )

        assertFalse(result)
        val stored = tasks.findById(task.id)!!
        assertEquals("Pay bill", stored.title)
        assertEquals(Priority.MEDIUM, stored.priority)
        assertEquals(1_000L, stored.updatedAt.toEpochMilli())
        assertEquals(0, activityLog.countOf(ActivityCategory.TASK_UPDATED))
    }

    @Test
    fun `operations on missing task return false`() = runBlocking {
        assertFalse(service.complete(123L))
        assertFalse(service.reopen(123L))
        assertFalse(service.archive(123L))
        assertFalse(service.unarchive(123L))
        assertFalse(service.delete(123L))
        assertFalse(
            service.update(123L, TaskEdit(title = "x", notes = null, priority = Priority.LOW, dueAt = null))
        )
        assertTrue(activityLog.entries.isEmpty())
    }
}
