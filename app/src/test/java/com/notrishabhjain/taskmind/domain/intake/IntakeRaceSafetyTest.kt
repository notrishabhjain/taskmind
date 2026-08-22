package com.notrishabhjain.taskmind.domain.intake

import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskQuery
import com.notrishabhjain.taskmind.domain.repository.DuplicateTaskException
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeRaceSafetyTest {

    private val timeProvider = FixedTimeProvider(start = 1_000L)
    private val tasks = FakeTaskRepository()
    private val reviews = FakeReviewRepository()
    private val activityLog = FakeActivityLogRepository()
    private val tags = FakeProjectTagRepository()

    private class CollisionInjectingRepository(
        private val backing: FakeTaskRepository,
        failuresRemaining: Int
    ) : TaskRepository {

        var failuresLeft = failuresRemaining
            private set

        override suspend fun insert(task: Task): Task {
            if (failuresLeft > 0) {
                failuresLeft--
                throw DuplicateTaskException(
                    task.sourceType,
                    requireNotNull(task.sourceRef),
                    task.titleKey
                )
            }
            return backing.insert(task)
        }

        override suspend fun update(task: Task) = backing.update(task)

        override suspend fun findById(id: Long): Task? = backing.findById(id)

        override suspend fun findByLogicalKey(
            sourceType: SourceType,
            sourceRef: String,
            titleKey: String
        ): Task? = backing.findByLogicalKey(sourceType, sourceRef, titleKey)

        override fun observeTasks(
            query: TaskQuery,
            dayStart: Instant,
            dayEnd: Instant
        ) = backing.observeTasks(query, dayStart, dayEnd)
    }

    private fun serviceWith(taskRepository: TaskRepository): TaskIntakeService =
        TaskIntakeService(
            taskRepository = taskRepository,
            reviewRepository = reviews,
            activityLogRepository = activityLog,
            projectTagRepository = tags,
            timeProvider = timeProvider
        )

    private suspend fun seedViaFunnel(sourceRef: String): Long {
        val service = serviceWith(tasks)
        val outcome = service.submit(
            TaskProposal.extracted(
                sourceType = SourceType.NOTIFICATION,
                sourceRef = sourceRef,
                title = "Pay electricity bill",
                confidence = 0.95,
                evidence = "pay the electricity bill today",
                sourceText = "please pay the electricity bill today"
            )
        )
        return requireNotNull((outcome as IntakeOutcome.Created).task.id)
    }

    private fun collisionProposal(sourceRef: String) = TaskProposal.extracted(
        sourceType = SourceType.NOTIFICATION,
        sourceRef = sourceRef,
        title = "Pay electricity bill",
        confidence = 0.9,
        evidence = "pay the electricity bill today",
        sourceText = "please pay the electricity bill today"
    )

    @Test
    fun `constraint collision after pre-check resolves to DuplicateDetected`() = runBlocking {
        val existingId = seedViaFunnel("wa:1")
        val service = serviceWith(CollisionInjectingRepository(tasks, failuresRemaining = 1))

        val outcome = service.submit(collisionProposal("wa:1"))

        assertTrue(outcome is IntakeOutcome.DuplicateDetected)
        assertEquals(existingId, (outcome as IntakeOutcome.DuplicateDetected).existingTaskId)
        assertEquals(1, tasks.size)
        assertEquals(0, reviews.all.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.DUPLICATE_DETECTED))
        assertEquals(0, activityLog.countOf(ActivityCategory.TASK_CREATED))
        assertEquals(0, activityLog.countOf(ActivityCategory.PROCESSING_FAILED))
    }

    @Test
    fun `collision without an existing task surfaces processing failure`() = runBlocking {
        val service = serviceWith(CollisionInjectingRepository(tasks, failuresRemaining = 1))

        val outcome = service.submit(collisionProposal("wa:ghost"))

        assertTrue(outcome is IntakeOutcome.Failed)
        assertEquals(0, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.PROCESSING_FAILED))
        assertEquals(0, activityLog.countOf(ActivityCategory.DUPLICATE_DETECTED))
    }

    @Test
    fun `subsequent inserts succeed once injected failure is consumed`() = runBlocking {
        val service = serviceWith(CollisionInjectingRepository(tasks, failuresRemaining = 1))

        val first = service.submit(collisionProposal("wa:a"))
        val second = service.submit(collisionProposal("wa:b"))

        assertTrue(first is IntakeOutcome.Failed)
        assertTrue(second is IntakeOutcome.Created)
        assertEquals(1, tasks.size)
    }
}
