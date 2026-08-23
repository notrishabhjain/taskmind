package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.intake.FakeProjectTagRepository
import com.notrishabhjain.taskmind.domain.intake.FakeReviewRepository
import com.notrishabhjain.taskmind.domain.intake.FakeTaskRepository
import com.notrishabhjain.taskmind.domain.intake.FixedTimeProvider
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.InferenceOrigin
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReviewServiceTest {

    private val timeProvider = FixedTimeProvider(start = 1_000L)
    private val tasks = FakeTaskRepository()
    private val reviews = FakeReviewRepository()
    private val activityLog = FakeActivityLogRepository()
    private val tags = FakeProjectTagRepository()

    @Before
    fun setUp() {
        runBlocking {
            reviews.insert(pendingReview(sourceRef = "wa:11", title = "Pay electricity bill"))
        }
    }

    private fun service(): ReviewService = ReviewService(
        reviewRepository = reviews,
        activityLogRepository = activityLog,
        timeProvider = timeProvider,
        taskIntakeService = TaskIntakeService(
            taskRepository = tasks,
            reviewRepository = reviews,
            activityLogRepository = activityLog,
            projectTagRepository = tags,
            timeProvider = timeProvider
        )
    )

    private fun pendingReview(
        sourceRef: String,
        title: String
    ) = ReviewItem(
        displayTitle = title,
        titleKey = title.lowercase(),
        dueAt = null,
        priority = com.notrishabhjain.taskmind.domain.model.Priority.HIGH,
        sourceType = SourceType.NOTIFICATION,
        sourceRef = sourceRef,
        sourceApp = "WhatsApp",
        sourceText = "please $title today",
        evidence = title.lowercase(),
        reasoning = "matched obligation",
        confidence = 0.55,
        createdAt = java.time.Instant.ofEpochMilli(500L)
    )

    @Test
    fun `accept routes through intake funnel and links resulting task`() = runBlocking {
        val reviewId = reviews.all.first().id

        val result = service().accept(reviewId)

        assertTrue(result is ReviewDecisionResult.Accepted)
        val task = (result as ReviewDecisionResult.Accepted).task
        assertEquals(InferenceOrigin.REVIEW_CONFIRMATION, task.inferenceOrigin)
        assertEquals(1.0, task.confidence!!, 0.0)

        val decided = reviews.findById(reviewId)!!
        assertEquals(ReviewStatus.ACCEPTED, decided.status)
        assertEquals(task.id, decided.resultingTaskId)
        assertEquals(1, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_ACCEPTED))
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_CREATED))
    }

    @Test
    fun `duplicate acceptance is idempotent`() = runBlocking {
        val service = service()
        val reviewId = reviews.all.first().id
        service.accept(reviewId)

        val secondAttempt = service.accept(reviewId)

        assertTrue(secondAttempt is ReviewDecisionResult.AlreadyDecided)
        assertEquals(1, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_ACCEPTED))
    }

    @Test
    fun `accepting content whose logical key matches an accepted task dismisses the new review`() = runBlocking {
        val service = service()
        val firstReviewId = reviews.all.first().id
        service.accept(firstReviewId)

        val secondReview = reviews.insert(
            pendingReview(sourceRef = "wa:11", title = "Pay Electricity Bill").copy(id = 0)
        )

        val result = service.accept(secondReview.id)

        assertTrue(result is ReviewDecisionResult.DuplicateOfExistingTask)
        assertEquals(1, tasks.size)
        assertEquals(ReviewStatus.DISMISSED, reviews.findById(secondReview.id)!!.status)
        assertNotNull(reviews.findById(secondReview.id)!!.resultingTaskId)
    }

    @Test
    fun `dismiss marks pending item dismissed and logs`() = runBlocking {
        val reviewId = reviews.all.first().id

        val result = service().dismiss(reviewId)

        assertTrue(result is ReviewDecisionResult.Dismissed)
        val decided = reviews.findById(reviewId)!!
        assertEquals(ReviewStatus.DISMISSED, decided.status)
        assertNull(decided.resultingTaskId)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_REJECTED))
        assertEquals(0, tasks.size)
    }

    @Test
    fun `dismissing twice reports AlreadyDecided without a second log`() = runBlocking {
        val service = service()
        val reviewId = reviews.all.first().id
        service.dismiss(reviewId)

        val secondAttempt = service.dismiss(reviewId)

        assertTrue(secondAttempt is ReviewDecisionResult.AlreadyDecided)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_REJECTED))
    }

    @Test
    fun `missing review reports Missing`() = runBlocking {
        val service = service()

        assertTrue(service.accept(999L) is ReviewDecisionResult.Missing)
        assertTrue(service.dismiss(999L) is ReviewDecisionResult.Missing)
        assertTrue(activityLog.entries.isEmpty())
    }
}
