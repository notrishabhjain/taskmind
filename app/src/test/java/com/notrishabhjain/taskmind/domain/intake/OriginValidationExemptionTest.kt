package com.notrishabhjain.taskmind.domain.intake

import com.notrishabhjain.taskmind.domain.model.ProposalOrigin
import com.notrishabhjain.taskmind.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginValidationExemptionTest {

    private val timeProvider = FixedTimeProvider(start = 1_000L)

    private fun service() = TaskIntakeService(
        taskRepository = FakeTaskRepository(),
        reviewRepository = FakeReviewRepository(),
        activityLogRepository = FakeActivityLogRepository(),
        projectTagRepository = FakeProjectTagRepository(),
        timeProvider = timeProvider
    )

    private fun rawProposal(origin: ProposalOrigin, confidence: Double?) = TaskProposal(
        origin = origin,
        sourceType = SourceType.NOTIFICATION,
        sourceRef = "ref-1",
        title = "Pay electricity bill",
        confidence = confidence,
        evidence = "not present anywhere",
        sourceText = "completely unrelated message"
    )

    @Test
    fun `exemption list is exactly the human-decided origins`() {
        val exempt = ProposalOrigin.entries.filterNot { originRequiresGeneratedValidation(it) }

        assertEquals(setOf(ProposalOrigin.MANUAL, ProposalOrigin.REVIEW_ACCEPTANCE), exempt.toSet())
    }

    @Test
    fun `every non-exempt origin runs the strict validation path`() = runBlocking {
        ProposalOrigin.entries
            .filter { originRequiresGeneratedValidation(it) }
            .forEach { origin ->
                val outcome = service().submit(rawProposal(origin, confidence = 0.95))

                assertTrue(
                    "origin $origin bypassed evidence validation",
                    outcome is IntakeOutcome.Rejected
                )
                assertEquals(
                    RejectionReason.EVIDENCE_INVALID,
                    (outcome as IntakeOutcome.Rejected).reason
                )
            }
    }

    @Test
    fun `every non-exempt origin with low confidence routes to review`() = runBlocking {
        ProposalOrigin.entries
            .filter { originRequiresGeneratedValidation(it) }
            .forEach { origin ->
                val outcome = service().submit(rawProposal(origin, confidence = 0.5))

                assertTrue(
                    "origin $origin skipped the confidence gate",
                    outcome is IntakeOutcome.RoutedToReview
                )
            }
    }

    @Test
    fun `manual proposals never run the generated validation path`() = runBlocking {
        val outcome = service().submit(
            TaskProposal.manual(title = "Buy milk", notes = null)
        )

        assertTrue(outcome is IntakeOutcome.Created)
    }
}
