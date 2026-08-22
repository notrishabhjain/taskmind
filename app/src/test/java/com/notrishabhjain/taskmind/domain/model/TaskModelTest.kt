package com.notrishabhjain.taskmind.domain.model

import com.notrishabhjain.taskmind.domain.intake.TitleNormalizer
import com.notrishabhjain.taskmind.domain.intake.ProposalOrigin
import com.notrishabhjain.taskmind.domain.intake.TaskProposal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskModelTest {

    private val now: Instant = Instant.ofEpochMilli(1_000_000L)

    @Test
    fun `priority values round-trip through name`() {
        Priority.values().forEach { priority ->
            assertEquals(priority, Priority.valueOf(priority.name))
        }
    }

    @Test
    fun `task status values round-trip through name`() {
        TaskStatus.values().forEach { status ->
            assertEquals(status, TaskStatus.valueOf(status.name))
        }
    }

    @Test
    fun `source type exposes all specification sources`() {
        val names = SourceType.values().map { it.name }.toSet()
        assertEquals(setOf("NOTIFICATION", "CALL", "MANUAL", "REVIEW"), names)
    }

    @Test
    fun `new task defaults are production safe`() {
        val task = Task(
            title = "Call mom",
            titleKey = "call mom",
            createdAt = now,
            updatedAt = now
        )

        assertEquals(TaskStatus.ACTIVE, task.status)
        assertEquals(SyncState.LOCAL_ONLY, task.syncState)
        assertEquals(Priority.MEDIUM, task.priority)
        assertEquals(SourceType.MANUAL, task.sourceType)
        assertNull(task.sourceRef)
        assertNull(task.confidence)
        assertNull(task.completedAt)
        assertNull(task.remoteId)
    }

    @Test
    fun `editing display fields preserves provenance`() {
        val autoTask = Task(
            id = 7L,
            title = "Pay electricity bill",
            titleKey = "pay electricity bill",
            sourceType = SourceType.NOTIFICATION,
            sourceRef = "whatsapp:msg-42",
            sourceApp = "WhatsApp",
            evidence = "pay the electricity bill today",
            confidence = 0.92,
            inferenceOrigin = InferenceOrigin.AUTOMATIC_INFERENCE,
            modelId = "extractor-v1",
            createdAt = now,
            updatedAt = now
        )

        val edited = autoTask.copy(
            title = "Pay electricity bill urgently",
            titleKey = TitleNormalizer.titleKey("Pay electricity bill urgently"),
            notes = "user added a note"
        )

        assertEquals("whatsapp:msg-42", edited.sourceRef)
        assertEquals("WhatsApp", edited.sourceApp)
        assertEquals("pay the electricity bill today", edited.evidence)
        assertEquals(0.92, edited.confidence!!, 0.0)
        assertEquals(InferenceOrigin.AUTOMATIC_INFERENCE, edited.inferenceOrigin)
        assertEquals("extractor-v1", edited.modelId)
        assertEquals(autoTask.createdAt, edited.createdAt)
    }

    @Test
    fun `manual proposals carry no fabricated provenance`() {
        val proposal = TaskProposal.manual(title = "Buy milk")

        assertEquals(ProposalOrigin.MANUAL, proposal.origin)
        assertEquals(SourceType.MANUAL, proposal.sourceType)
        assertNull(proposal.sourceRef)
        assertNull(proposal.evidence)
        assertNull(proposal.confidence)
        assertEquals(InferenceOrigin.HUMAN_INPUT, proposal.inferenceOrigin)
    }
}
