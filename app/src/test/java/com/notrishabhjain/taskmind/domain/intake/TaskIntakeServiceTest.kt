package com.notrishabhjain.taskmind.domain.intake

import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.InferenceOrigin
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskIntakeServiceTest {

    private val timeProvider = FixedTimeProvider(start = 1_000L)
    private val tasks = FakeTaskRepository()
    private val reviews = FakeReviewRepository()
    private val activityLog = FakeActivityLogRepository()
    private val tags = FakeProjectTagRepository()

    private fun service() = TaskIntakeService(
        taskRepository = tasks,
        reviewRepository = reviews,
        activityLogRepository = activityLog,
        projectTagRepository = tags,
        timeProvider = timeProvider
    )

    private fun extractionProposal(
        title: String = "Pay electricity bill",
        sourceRef: String = "wa:1",
        confidence: Double? = 0.9,
        evidence: String? = "pay the electricity bill today",
        sourceText: String = "hey please pay the electricity bill today before 6"
    ) = TaskProposal.extracted(
        sourceType = SourceType.NOTIFICATION,
        sourceRef = sourceRef,
        title = title,
        confidence = confidence,
        evidence = evidence,
        sourceText = sourceText
    )

    @Test
    fun `manual task is created with normalized title and key`() = runBlocking {
        val outcome = service().submit(TaskProposal.manual(title = "  Please   call MOM "))

        assertTrue(outcome is IntakeOutcome.Created)
        val task = (outcome as IntakeOutcome.Created).task
        assertEquals("Please call MOM", task.title)
        assertEquals("call mom", task.titleKey)
        assertEquals(SourceType.MANUAL, task.sourceType)
        assertEquals(1_000L, task.createdAt.toEpochMilli())
        assertEquals(1_000L, task.updatedAt.toEpochMilli())
    }

    @Test
    fun `blank manual title is rejected and logged`() = runBlocking {
        val service = service()

        val outcome = service.submit(TaskProposal.manual(title = "   "))

        assertTrue(outcome is IntakeOutcome.Rejected)
        assertEquals(RejectionReason.BLANK_TITLE, (outcome as IntakeOutcome.Rejected).reason)
        assertEquals(0, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_REJECTED))
    }

    @Test
    fun `extraction without source reference is rejected`() = runBlocking {
        val proposal = TaskProposal.extracted(
            sourceType = SourceType.CALL,
            sourceRef = "",
            title = "Call back dad",
            confidence = 0.9,
            evidence = "call me back",
            sourceText = "call me back when free"
        )

        val outcome = service().submit(proposal)

        assertEquals(IntakeOutcome.Rejected(RejectionReason.MISSING_SOURCE_REF), outcome)
        assertEquals(0, tasks.size)
    }

    @Test
    fun `high confidence extraction auto creates with provenance`() = runBlocking {
        val outcome = service().submit(extractionProposal())

        assertTrue(outcome is IntakeOutcome.Created)
        val task = (outcome as IntakeOutcome.Created).task
        assertEquals(InferenceOrigin.AUTOMATIC_INFERENCE, task.inferenceOrigin)
        assertEquals(0.9, task.confidence!!, 0.0)
        assertEquals("pay the electricity bill today", task.evidence)
        assertEquals("wa:1", task.sourceRef)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_CREATED))
    }

    @Test
    fun `mid confidence extraction routes to review`() = runBlocking {
        val outcome = service().submit(extractionProposal(confidence = 0.55))

        assertTrue(outcome is IntakeOutcome.RoutedToReview)
        val item = (outcome as IntakeOutcome.RoutedToReview).reviewItem
        assertEquals(ReviewStatus.PENDING, item.status)
        assertEquals("pay electricity bill", item.titleKey)
        assertEquals(1, reviews.all.size)
        assertEquals(0, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_SENT_TO_REVIEW))
    }

    @Test
    fun `low confidence extraction is rejected`() = runBlocking {
        val outcome = service().submit(extractionProposal(confidence = 0.2))

        assertEquals(IntakeOutcome.Rejected(RejectionReason.BELOW_CONFIDENCE_THRESHOLD), outcome)
        assertEquals(0, tasks.size)
        assertEquals(0, reviews.all.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_REJECTED))
    }

    @Test
    fun `missing confidence on extraction routes to review`() = runBlocking {
        val outcome = service().submit(extractionProposal(confidence = null))

        assertTrue(outcome is IntakeOutcome.RoutedToReview)
        assertEquals(0, tasks.size)
    }

    @Test
    fun `evidence absent from source rejects creation`() = runBlocking {
        val outcome = service().submit(
            extractionProposal(evidence = "buy groceries tonight")
        )

        assertEquals(IntakeOutcome.Rejected(RejectionReason.EVIDENCE_INVALID), outcome)
        assertEquals(0, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.EVIDENCE_VALIDATION_FAILED))
    }

    @Test
    fun `empty evidence on extraction rejects creation`() = runBlocking {
        val outcome = service().submit(extractionProposal(evidence = ""))

        assertEquals(IntakeOutcome.Rejected(RejectionReason.EVIDENCE_INVALID), outcome)
        assertEquals(0, tasks.size)
    }

    @Test
    fun `identical source ref and title deduplicates`() = runBlocking {
        val service = service()

        val first = service.submit(extractionProposal(sourceRef = "wa:42", title = "Pay electricity bill"))
        val second = service.submit(extractionProposal(sourceRef = "wa:42", title = "please Pay Electricity Bill"))

        assertTrue(first is IntakeOutcome.Created)
        assertTrue(second is IntakeOutcome.DuplicateDetected)
        val duplicate = second as IntakeOutcome.DuplicateDetected
        assertEquals((first as IntakeOutcome.Created).task.id, duplicate.existingTaskId)
        assertEquals(1, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.DUPLICATE_DETECTED))
    }

    @Test
    fun `different titles from same source message create distinct tasks`() = runBlocking {
        val service = service()

        val first = service.submit(extractionProposal(sourceRef = "wa:7", title = "Pay electricity bill"))
        val second = service.submit(extractionProposal(sourceRef = "wa:7", title = "Buy groceries"))

        assertTrue(first is IntakeOutcome.Created)
        assertTrue(second is IntakeOutcome.Created)
        assertEquals(2, tasks.size)
    }

    @Test
    fun `manual tasks can repeat despite identical normalized titles`() = runBlocking {
        val service = service()

        val first = service.submit(TaskProposal.manual(title = "Water the plants"))
        val second = service.submit(TaskProposal.manual(title = "water   the plants!"))

        assertTrue(first is IntakeOutcome.Created)
        assertTrue(second is IntakeOutcome.Created)
        assertEquals(2, tasks.size)
    }

    @Test
    fun `accepted review flows through same funnel and links result`() = runBlocking {
        val service = service()

        val reviewOutcome = service.submit(
            extractionProposal(confidence = 0.5)
        )
        val reviewItem = (reviewOutcome as IntakeOutcome.RoutedToReview).reviewItem
        timeProvider.advanceBy(10)

        val accepted = service.submit(TaskProposal.fromAcceptedReview(reviewItem))

        assertTrue(accepted is IntakeOutcome.Created)
        val task = (accepted as IntakeOutcome.Created).task
        assertEquals(InferenceOrigin.REVIEW_CONFIRMATION, task.inferenceOrigin)
        assertEquals(1.0, task.confidence!!, 0.0)
        assertEquals(task.id, reviews.findById(reviewItem.id)?.resultingTaskId)
        assertEquals(ReviewStatus.ACCEPTED, reviews.findById(reviewItem.id)?.status)
    }

    @Test
    fun `accepting a review whose content already exists dismisses it as duplicate`() = runBlocking {
        val service = service()
        service.submit(extractionProposal(sourceRef = "wa:99"))
        val reviewItem = (
            service.submit(extractionProposal(sourceRef = "call-log:5", confidence = 0.6))
                as IntakeOutcome.RoutedToReview
            ).reviewItem.copy(sourceRef = "wa:99")
        val reInserted = reviews.insert(reviewItem.copy(id = 0))

        val outcome = service.submit(TaskProposal.fromAcceptedReview(reInserted))

        assertTrue(outcome is IntakeOutcome.DuplicateDetected)
        assertEquals(ReviewStatus.DISMISSED, reviews.findById(reInserted.id)?.status)
        assertEquals(1, tasks.size)
    }
}
