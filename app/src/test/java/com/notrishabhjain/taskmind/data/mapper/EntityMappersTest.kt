package com.notrishabhjain.taskmind.data.mapper

import com.notrishabhjain.taskmind.data.db.entity.ActivityLogEntity
import com.notrishabhjain.taskmind.data.db.entity.ReviewItemEntity
import com.notrishabhjain.taskmind.data.db.entity.TagEntity
import com.notrishabhjain.taskmind.data.db.entity.TaskEntity
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.InferenceOrigin
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.Project
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.SyncState
import com.notrishabhjain.taskmind.domain.model.Tag
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EntityMappersTest {

    private val instantA: Instant = Instant.ofEpochMilli(1_712_345_678_901L)
    private val instantB: Instant = Instant.ofEpochMilli(1_800_000_000_000L)

    @Test
    fun `fully populated task round-trips through entity`() {
        val task = Task(
            id = 12L,
            title = "Pay electricity bill",
            titleKey = "pay electricity bill",
            notes = "before the 5th",
            dueAt = instantB,
            priority = Priority.URGENT,
            status = TaskStatus.ACTIVE,
            projectId = 3L,
            tagIds = listOf(5L, 6L, 7L),
            recurrenceRule = "FREQ=WEEKLY",
            reminderAt = instantB.minusSeconds(3_600),
            parentTaskId = null,
            sortOrder = 4,
            sourceType = SourceType.NOTIFICATION,
            sourceRef = "wa:42",
            sourceLabel = "WhatsApp",
            sourceApp = "com.whatsapp",
            evidence = "pay the electricity bill today",
            confidence = 0.92,
            inferenceOrigin = InferenceOrigin.AUTOMATIC_INFERENCE,
            modelId = "extractor-v1",
            remoteId = null,
            syncState = SyncState.LOCAL_ONLY,
            completedAt = null,
            createdAt = instantA,
            updatedAt = instantB
        )

        val restored = task.toEntity().toDomain(tagIds = listOf(5L, 6L, 7L))

        assertEquals(task, restored)
    }

    @Test
    fun `minimal nullable fields are preserved as null`() {
        val task = Task(
            title = "Call mom",
            titleKey = "call mom",
            createdAt = instantA,
            updatedAt = instantA
        )

        val entity: TaskEntity = task.toEntity()

        assertNull(entity.notes)
        assertNull(entity.dueAt)
        assertNull(entity.projectId)
        assertNull(entity.recurrenceRule)
        assertNull(entity.reminderAt)
        assertNull(entity.parentTaskId)
        assertNull(entity.sortOrder)
        assertNull(entity.sourceRef)
        assertNull(entity.sourceLabel)
        assertNull(entity.sourceApp)
        assertNull(entity.evidence)
        assertNull(entity.confidence)
        assertNull(entity.inferenceOrigin)
        assertNull(entity.modelId)
        assertNull(entity.remoteId)
        assertNull(entity.completedAt)

        val restored = entity.toDomain(tagIds = emptyList())
        assertEquals(task, restored)
    }

    @Test
    fun `all enum values survive serialization`() {
        Priority.entries.forEach { value ->
            assertEquals(
                value,
                Task(title = "t", titleKey = "t", priority = value, createdAt = instantA, updatedAt = instantA)
                    .toEntity().toDomain().priority
            )
        }
        TaskStatus.entries.forEach { value ->
            assertEquals(
                value,
                Task(title = "t", titleKey = "t", status = value, createdAt = instantA, updatedAt = instantA)
                    .toEntity().toDomain().status
            )
        }
        SourceType.entries.forEach { value ->
            assertEquals(
                value,
                Task(title = "t", titleKey = "t", sourceType = value, createdAt = instantA, updatedAt = instantA)
                    .toEntity().toDomain().sourceType
            )
        }
        SyncState.entries.forEach { value ->
            assertEquals(
                value,
                Task(title = "t", titleKey = "t", syncState = value, createdAt = instantA, updatedAt = instantA)
                    .toEntity().toDomain().syncState
            )
        }
        (InferenceOrigin.entries + null).forEach { value ->
            assertEquals(
                value,
                Task(title = "t", titleKey = "t", inferenceOrigin = value, createdAt = instantA, updatedAt = instantA)
                    .toEntity().toDomain().inferenceOrigin
            )
        }
    }

    @Test
    fun `tag ids ride along through the mapping`() {
        val task = Task(
            id = 9L,
            title = "Groceries",
            titleKey = "groceries",
            tagIds = emptyList(),
            createdAt = instantA,
            updatedAt = instantA
        )

        val stored = task.toEntity()
        val withTags = stored.toDomain(tagIds = listOf(2L, 4L))

        assertEquals(emptyList<Long>(), task.tagIds)
        assertEquals(listOf(2L, 4L), withTags.tagIds)
    }

    @Test
    fun `malformed enum data fails loudly instead of inventing a fallback`() {
        val corrupted = Task(
            id = 1L,
            title = "t",
            titleKey = "t",
            status = TaskStatus.ACTIVE,
            createdAt = instantA,
            updatedAt = instantA
        ).toEntity()
            .copy(status = "NOT_A_REAL_STATUS")

        assertThrows(IllegalStateException::class.java) { corrupted.toDomain() }
    }

    @Test
    fun `review item round-trips through entity`() {
        val item = ReviewItem(
            id = 21L,
            displayTitle = "Pay electricity bill",
            titleKey = "pay electricity bill",
            notes = null,
            dueAt = instantB,
            priority = Priority.HIGH,
            projectId = 2L,
            sourceType = SourceType.CALL,
            sourceRef = "call:555",
            sourceLabel = "Dad",
            sourceApp = "Phone",
            sourceText = "please pay the electricity bill today",
            evidence = "pay the electricity bill today",
            reasoning = "matched obligation phrase",
            confidence = 0.55,
            status = ReviewStatus.PENDING,
            resultingTaskId = null,
            createdAt = instantA,
            decidedAt = null
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun `decided review item keeps its decision timestamps`() {
        val entity = ReviewItemEntity(
            id = 1L,
            displayTitle = "x",
            titleKey = "x",
            notes = null,
            dueAt = null,
            priority = "LOW",
            projectId = null,
            sourceType = "CALL",
            sourceRef = "c:1",
            sourceLabel = null,
            sourceApp = null,
            sourceText = null,
            evidence = null,
            reasoning = null,
            confidence = 0.41,
            status = "DISMISSED",
            resultingTaskId = 77L,
            createdAt = instantA.toEpochMilli(),
            decidedAt = instantB.toEpochMilli()
        )

        val restored = entity.toDomain()

        assertEquals(ReviewStatus.DISMISSED, restored.status)
        assertEquals(77L, restored.resultingTaskId!!)
        assertEquals(instantB, restored.decidedAt)
    }

    @Test
    fun `activity log entry round-trips through entity`() {
        val entry = com.notrishabhjain.taskmind.domain.model.ActivityLogEntry(
            id = 300L,
            category = ActivityCategory.INTAKE_SENT_TO_REVIEW,
            message = "\"Pay bill\" needs your confirmation",
            detail = "confidence=0.61",
            taskId = null,
            createdAt = instantA
        )

        val entity: ActivityLogEntity = entry.toEntity()

        assertNull(entity.taskId)
        assertEquals(entry, entity.toDomain())
    }

    @Test
    fun `project and tag mappers preserve identity fields`() {
        val projectEntity = com.notrishabhjain.taskmind.data.db.entity.ProjectEntity(
            id = 8L,
            name = "Home",
            nameKey = "home"
        )
        val tagEntity = TagEntity(id = 9L, name = "Errands", nameKey = "errands")

        val project: Project = projectEntity.toDomain()
        val tag: Tag = tagEntity.toDomain()

        assertEquals(Project(id = 8L, name = "Home", nameKey = "home"), project)
        assertEquals(Tag(id = 9L, name = "Errands", nameKey = "errands"), tag)
    }
}
