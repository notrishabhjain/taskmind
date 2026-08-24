package com.notrishabhjain.taskmind.notification

import com.notrishabhjain.taskmind.data.processor.NotificationTaskProcessor
import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.intake.FakeNotificationCaptureRepository
import com.notrishabhjain.taskmind.domain.intake.FakeProjectTagRepository
import com.notrishabhjain.taskmind.domain.intake.FakeReviewRepository
import com.notrishabhjain.taskmind.domain.intake.FakeTaskRepository
import com.notrishabhjain.taskmind.domain.intake.FixedTimeProvider
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.repository.CaptureInsertOutcome
import com.notrishabhjain.taskmind.domain.service.CaptureProcessingCoordinator
import com.notrishabhjain.taskmind.domain.service.DeterministicNotificationTaskExtractor
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end proof of the 4C gate feeding the untouched 4D/4E pipeline:
 *
 *   CaptureFilter -> NotificationCanonicalizer -> CaptureDeduplication ->
 *   repository persistence -> CaptureProcessingCoordinator (4D) ->
 *   NotificationTaskProcessor (4E) -> TaskIntakeService -> Task
 */
class CapturePipelineIntegrationTest {

    private val time = FixedTimeProvider(1_700_000_000_000L)
    private val zone = ZoneId.of("UTC")
    private val tasks = FakeTaskRepository()
    private val reviews = FakeReviewRepository()
    private val activityLog = FakeActivityLogRepository()
    private val tags = FakeProjectTagRepository()
    private val captures = FakeNotificationCaptureRepository()

    private val selfPackage = "com.notrishabhjain.taskmind"
    private val whatsappInput = RawNotificationInput(
        sourcePackage = "com.whatsapp",
        notificationKey = "0|com.whatsapp|101|null|10001",
        postTimeMillis = 100L,
        title = "Rishabh Jain",
        text = "Please call Rahul tomorrow at 4 PM",
        appLabel = "WhatsApp"
    )

    private fun filter(blocked: Set<String> = emptySet(), allowed: Set<String> = emptySet()) =
        CaptureFilter(NotificationCapturePolicy(selfPackage = selfPackage, blockedPackages = blocked, allowedPackages = allowed))

    private fun buildCoordinator(): CaptureProcessingCoordinator = CaptureProcessingCoordinator(
        captures = captures,
        processor = NotificationTaskProcessor(
            extractor = DeterministicNotificationTaskExtractor(),
            taskIntakeService = TaskIntakeService(tasks, reviews, activityLog, tags, time),
            timeProvider = time,
            zoneId = zone
        ),
        activityLogRepository = activityLog,
        timeProvider = time
    )

    /** Mirrors TaskMindNotificationListenerService.handlePosted using JVM seams. */
    private suspend fun deliver(input: RawNotificationInput): CaptureRelation {
        val filter = filter()
        if (filter.decide(input) == CaptureDecision.IGNORE) return CaptureRelation.FRESH_CAPTURE
        val incoming = NotificationCanonicalizer.toCapture(input, capturedAt = time.now())
        val latest = captures.findLatestByIdentity(input.sourcePackage, input.notificationKey)
        val relation = CaptureDeduplication.classify(latest, incoming)
        val outcome = captures.insertIfAbsent(incoming)
        if (outcome is CaptureInsertOutcome.Inserted && relation == CaptureRelation.NEW_VERSION) {
            activityLog.appendVersionEvent(latest?.id)
        }
        return relation
    }

    private suspend fun drain() {
        buildCoordinator().runBatch()
    }

    private suspend fun FakeActivityLogRepository.appendVersionEvent(previousId: Long?) {
        append(
            com.notrishabhjain.taskmind.domain.model.ActivityLogEntry(
                category = ActivityCategory.CAPTURE_VERSIONED,
                message = "Updated notification captured as a new version",
                detail = previousId?.let { "previous capture #$it" },
                taskId = null,
                createdAt = time.now()
            )
        )
    }

    @Test
    fun `whatsapp action notification flows end to end into a task`() = runTest {
        assertEquals(CaptureDecision.ACCEPT, filter().decide(whatsappInput))
        assertEquals(CaptureRelation.FRESH_CAPTURE, deliver(whatsappInput))

        drain()

        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.PROCESSED, stored.state)
        assertNotNull(stored.resultingTaskId)
        val task = tasks.all.single()
        assertEquals("Call Rahul", task.title)
        assertEquals(com.notrishabhjain.taskmind.domain.model.SourceType.NOTIFICATION, task.sourceType)
        assertEquals(stored.sourceRef, task.sourceRef)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_CREATED))
    }

    @Test
    fun `exact duplicate delivery never creates a second capture or task`() = runTest {
        assertEquals(CaptureRelation.FRESH_CAPTURE, deliver(whatsappInput))
        drain()

        // True redelivery: identical postTime and content -> identical idempotency key.
        assertEquals(CaptureRelation.EXACT_DUPLICATE, deliver(whatsappInput))

        drain()
        assertEquals(1, captures.all.size)
        assertEquals(1, tasks.size)
    }

    @Test
    fun `notification update versions the capture and resolves to the existing task`() = runTest {
        deliver(whatsappInput)
        drain()

        val updated = whatsappInput.copy(text = "Please call Rahul tomorrow at 5 PM")
        assertEquals(CaptureRelation.NEW_VERSION, deliver(updated))
        assertEquals(2, captures.all.size)
        assertEquals(CaptureState.PROCESSED, captures.findById(1L)!!.state)

        drain()

        assertEquals(1, tasks.size)
        val versionRow = captures.findById(2L)!!
        assertEquals(CaptureState.PROCESSED, versionRow.state)
        assertEquals(1L, versionRow.resultingTaskId!!)
        assertEquals(1, activityLog.countOf(ActivityCategory.DUPLICATE_DETECTED))
        assertEquals(1, activityLog.entries.count { it.category == ActivityCategory.CAPTURE_VERSIONED })
    }

    @Test
    fun `self notifications are filtered before capture`() {
        val self = whatsappInput.copy(sourcePackage = selfPackage)
        assertEquals(CaptureDecision.IGNORE, filter().decide(self))
    }

    @Test
    fun `empty content is ignored`() {
        assertEquals(
            CaptureDecision.IGNORE,
            filter().decide(whatsappInput.copy(title = "", text = ""))
        )
    }

    @Test
    fun `blocklisted packages are ignored`() {
        val blockedFilter = filter(blocked = setOf("com.spam"))
        assertEquals(
            CaptureDecision.IGNORE,
            blockedFilter.decide(whatsappInput.copy(sourcePackage = "com.spam"))
        )
    }

    @Test
    fun `active allowlist admits known packages and rejects unknown ones`() {
        val allowFilter = filter(allowed = setOf("com.whatsapp"))
        assertEquals(CaptureDecision.ACCEPT, allowFilter.decide(whatsappInput))
        assertEquals(
            CaptureDecision.IGNORE,
            allowFilter.decide(whatsappInput.copy(sourcePackage = "com.unknown"))
        )
    }

    @Test
    fun `informational otp notification ends rejected through the full pipeline`() = runTest {
        deliver(whatsappInput.copy(notificationKey = "k-otp", text = "Your OTP is 482913."))
        drain()

        assertEquals(CaptureState.REJECTED, captures.findById(1L)!!.state)
        assertEquals(0, tasks.size)
        assertTrue(activityLog.entries.none { (it.message + " " + (it.detail ?: "")).contains("482913") })
    }
}
