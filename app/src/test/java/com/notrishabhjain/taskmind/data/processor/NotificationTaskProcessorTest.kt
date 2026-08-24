package com.notrishabhjain.taskmind.data.processor

import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.intake.FakeNotificationCaptureRepository
import com.notrishabhjain.taskmind.domain.intake.FakeProjectTagRepository
import com.notrishabhjain.taskmind.domain.intake.FakeReviewRepository
import com.notrishabhjain.taskmind.domain.intake.FakeTaskRepository
import com.notrishabhjain.taskmind.domain.intake.FixedTimeProvider
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.InferenceOrigin
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.repository.DuplicateTaskException
import com.notrishabhjain.taskmind.domain.service.CaptureProcessingCoordinator
import com.notrishabhjain.taskmind.domain.service.CaptureProcessingResult
import com.notrishabhjain.taskmind.domain.service.DeterministicNotificationTaskExtractor
import com.notrishabhjain.taskmind.domain.service.NotificationExtraction
import com.notrishabhjain.taskmind.domain.service.NotificationExtractionOutcome
import com.notrishabhjain.taskmind.domain.service.NotificationTaskExtractor
import com.notrishabhjain.taskmind.domain.service.ReviewService
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationTaskProcessorTest {

    private val time = FixedTimeProvider(1_700_000_000_000L)
    private val zone = ZoneId.of("UTC")
    private val tasks = FakeTaskRepository()
    private val reviews = FakeReviewRepository()
    private val activityLog = FakeActivityLogRepository()
    private val tags = FakeProjectTagRepository()
    private val captures = FakeNotificationCaptureRepository()

    private val intake = TaskIntakeService(
        taskRepository = tasks,
        reviewRepository = reviews,
        activityLogRepository = activityLog,
        projectTagRepository = tags,
        timeProvider = time
    )

    private var extractionOutcome: NotificationExtractionOutcome =
        NotificationExtractionOutcome.NotActionable("none")

    private val stubExtractor = NotificationTaskExtractor { _, _, _ -> extractionOutcome }

    private lateinit var processor: NotificationTaskProcessor

    @Before
    fun setUp() {
        extractionOutcome = NotificationExtractionOutcome.NotActionable("none")
        processor = NotificationTaskProcessor(stubExtractor, intake, time, zone)
    }

    private fun extraction(
        title: String,
        confidence: Double,
        evidence: String,
        dueAt: Instant? = null,
        reasoning: String = "Detected action."
    ) = NotificationExtractionOutcome.Actionable(
        NotificationExtraction(
            title = title,
            notes = null,
            dueAt = dueAt,
            confidence = confidence,
            evidence = evidence,
            reasoning = reasoning,
            modelId = DeterministicModelIdForTests
        )
    )

    private fun capture(
        idempotencyKey: String = "k1",
        canonical: String = "Please call Rahul tomorrow"
    ): NotificationCapture = NotificationCapture(
        idempotencyKey = idempotencyKey,
        sourcePackage = "com.whatsapp",
        sourceAppLabel = "WhatsApp",
        notificationKey = "n-$idempotencyKey",
        postTime = Instant.ofEpochMilli(100L),
        canonicalSourceText = canonical,
        contentHash = "hash-$idempotencyKey",
        sourceRef = "notification:com.whatsapp:n-$idempotencyKey",
        createdAt = Instant.ofEpochMilli(100L),
        updatedAt = Instant.ofEpochMilli(100L)
    )

    @Test
    fun `high confidence proposal creates a task through the funnel`() = runBlocking {
        extractionOutcome = extraction("Call Rahul", 0.85, "call Rahul")
        val result = processor.process(capture())

        assertTrue(result is CaptureProcessingResult.Processed)
        assertEquals(1L, (result as CaptureProcessingResult.Processed).resultingTaskId)

        val task = tasks.all.single()
        assertEquals("Call Rahul", task.title)
        assertEquals(SourceType.NOTIFICATION, task.sourceType)
        assertEquals("notification:com.whatsapp:n-k1", task.sourceRef)
        assertEquals("call Rahul", task.evidence)
        assertEquals(0.85, task.confidence!!, 0.001)
        assertEquals(InferenceOrigin.AUTOMATIC_INFERENCE, task.inferenceOrigin)
        assertEquals("com.whatsapp", task.sourceApp)
        assertEquals("WhatsApp", task.sourceLabel)
        assertEquals(1, activityLog.countOf(ActivityCategory.TASK_CREATED))
    }

    @Test
    fun `mid confidence proposal routes to the existing review inbox`() = runBlocking {
        extractionOutcome = extraction("Call Rahul", 0.60, "call Rahul")
        val result = processor.process(capture())

        assertTrue(result is CaptureProcessingResult.ReviewRequired)
        val review = reviews.all.single()
        assertEquals("Call Rahul", review.displayTitle)
        assertEquals(0.60, review.confidence!!, 0.001)
        assertEquals("call Rahul", review.evidence)
        assertEquals("notification:com.whatsapp:n-k1", review.sourceRef)
        assertEquals(0, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_SENT_TO_REVIEW))
    }

    @Test
    fun `duplicate detection yields processed with existing task id and no second task`() = runBlocking {
        extractionOutcome = extraction("Call Rahul", 0.85, "call Rahul")
        processor.process(capture())
        val secondVersion = capture(idempotencyKey = "k2").copy(sourceRef = "notification:com.whatsapp:n-k1")

        val result = processor.process(secondVersion)

        assertTrue(result is CaptureProcessingResult.Processed)
        assertEquals(1L, (result as CaptureProcessingResult.Processed).resultingTaskId)
        assertEquals(1, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.DUPLICATE_DETECTED))
    }

    @Test
    fun `intake rejection maps to rejected result`() = runBlocking {
        extractionOutcome = extraction("Call Rahul", 0.85, "evidence that never occurred in source")
        val result = processor.process(capture())

        assertTrue(result is CaptureProcessingResult.Rejected)
        assertEquals(0, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.EVIDENCE_VALIDATION_FAILED))
    }

    @Test
    fun `not actionable notifications are rejected without proposals`() = runBlocking {
        extractionOutcome = NotificationExtractionOutcome.NotActionable("otp")
        val result = processor.process(capture())

        assertTrue(result is CaptureProcessingResult.Rejected)
        assertEquals(0, tasks.size)
        assertEquals(0, reviews.all.size)
    }

    @Test
    fun `intake failed outcome preserves retry semantics`() = runBlocking {
        val collidingTasks = object : FakeTaskRepository() {
            override suspend fun insert(task: com.notrishabhjain.taskmind.domain.model.Task) =
                throw DuplicateTaskException(task.sourceType, task.sourceRef!!, task.titleKey)
        }
        val brokenIntake = TaskIntakeService(
            taskRepository = collidingTasks,
            reviewRepository = reviews,
            activityLogRepository = activityLog,
            projectTagRepository = tags,
            timeProvider = time
        )
        val brokenProcessor = NotificationTaskProcessor(stubExtractor, brokenIntake, time, zone)
        extractionOutcome = extraction("Call Rahul", 0.85, "call Rahul")

        val result = brokenProcessor.process(capture())

        assertTrue(result is CaptureProcessingResult.RetryableFailure)
        assertEquals("duplicate-collision-without-existing-task", (result as CaptureProcessingResult.RetryableFailure).reason)
    }

    @Test
    fun `cancellation from extraction propagates without side effects`() = runBlocking {
        val cancellingExtractor = NotificationTaskExtractor { _, _, _ ->
            throw CancellationException("worker cancelled")
        }
        val cancellingProcessor = NotificationTaskProcessor(cancellingExtractor, intake, time, zone)

        val thrown = runCatching { cancellingProcessor.process(capture()) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(0, tasks.size)
        assertEquals(0, activityLog.entries.size)
    }

    @Test
    fun `unexpected extractor exception propagates to coordinator policy`() = runBlocking {
        val throwingExtractor = NotificationTaskExtractor { _, _, _ ->
            throw IllegalStateException("boom")
        }
        val throwingProcessor = NotificationTaskProcessor(throwingExtractor, intake, time, zone)

        val thrown = runCatching { throwingProcessor.process(capture()) }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `due date flows into created task`() = runBlocking {
        val due = java.time.ZonedDateTime.of(2026, 8, 25, 16, 0, 0, 0, zone).toInstant()
        extractionOutcome = extraction("Call Rahul", 0.85, "call Rahul", dueAt = due)
        val result = processor.process(capture()) as CaptureProcessingResult.Processed

        assertEquals(due, tasks.findById(result.resultingTaskId!!)!!.dueAt)
    }

    @Test
    fun `end to end capture becomes a processed task exactly once`() = runBlocking {
        val realProcessor = buildRealPipeline()
        captures.insertIfAbsent(capture(canonical = "Please call Rahul tomorrow at 4 PM"))
        val coordinator = CaptureProcessingCoordinator(
            captures = captures,
            processor = realProcessor,
            activityLogRepository = activityLog,
            timeProvider = time,
            processingTimeout = java.time.Duration.ofMinutes(15)
        )

        val first = coordinator.runBatch()
        assertEquals(1, first.processed)
        val stored = captures.findById(1L)!!
        assertEquals(CaptureState.PROCESSED, stored.state)
        assertNotNull(stored.resultingTaskId)
        assertEquals(1, tasks.size)

        val second = coordinator.runBatch()
        assertEquals(0, second.attempted)
        assertEquals(1, tasks.size)
    }

    @Test
    fun `versioned duplicate capture resolves to existing task`() = runBlocking {
        val realProcessor = buildRealPipeline()
        val coordinator = CaptureProcessingCoordinator(
            captures = captures,
            processor = realProcessor,
            activityLogRepository = activityLog,
            timeProvider = time
        )
        val original = capture(canonical = "Please call Rahul tomorrow at 4 PM")
        captures.insertIfAbsent(original)
        coordinator.runBatch()

        val updatedVersion = original.copy(
            idempotencyKey = "k2",
            postTime = Instant.ofEpochMilli(200L),
            contentHash = "hash-2"
        )
        captures.insertIfAbsent(updatedVersion.copy(state = CaptureState.CAPTURED))
        coordinator.runBatch()

        assertEquals(1, tasks.size)
        val versionedRow = captures.findById(2L)!!
        assertEquals(CaptureState.PROCESSED, versionedRow.state)
        assertEquals(1L, versionedRow.resultingTaskId!!)
    }

    @Test
    fun `informational capture ends rejected through the full pipeline`() = runBlocking {
        val realProcessor = buildRealPipeline()
        captures.insertIfAbsent(capture(canonical = "Your OTP is 482913."))
        val coordinator = CaptureProcessingCoordinator(
            captures = captures,
            processor = realProcessor,
            activityLogRepository = activityLog,
            timeProvider = time
        )

        coordinator.runBatch()

        assertEquals(CaptureState.REJECTED, captures.findById(1L)!!.state)
        assertEquals(0, tasks.size)
    }

    @Test
    fun `review route accepts through ReviewService into the intake funnel`() = runBlocking {
        val realProcessor = buildRealPipeline()
        captures.insertIfAbsent(capture(canonical = "Meeting moved to Friday at 3 PM."))
        val reviewService = ReviewService(reviews, activityLog, time, intake)
        val coordinator = CaptureProcessingCoordinator(
            captures = captures,
            processor = realProcessor,
            activityLogRepository = activityLog,
            timeProvider = time
        )

        coordinator.runBatch()
        assertEquals(CaptureState.REVIEWED, captures.findById(1L)!!.state)
        val pending = reviews.all.single()
        assertEquals(com.notrishabhjain.taskmind.domain.model.ReviewStatus.PENDING, pending.status)

        val decision = reviewService.accept(pending.id)

        assertTrue(decision is com.notrishabhjain.taskmind.domain.service.ReviewDecisionResult.Accepted)
        assertEquals(1, tasks.size)
        assertEquals(1, activityLog.countOf(ActivityCategory.INTAKE_ACCEPTED))
    }

    @Test
    fun `activity log never contains notification bodies`() = runBlocking {
        val secretBody = "482913"
        val realProcessor = buildRealPipeline()
        captures.insertIfAbsent(capture(canonical = "Please call Rahul tomorrow at 4 PM"))
        captures.insertIfAbsent(capture(idempotencyKey = "k2", canonical = "Your OTP is $secretBody."))
        val coordinator = CaptureProcessingCoordinator(
            captures = captures,
            processor = realProcessor,
            activityLogRepository = activityLog,
            timeProvider = time
        )
        coordinator.runBatch()
        coordinator.runBatch()

        val leaked = activityLog.entries.any { entry ->
            (entry.message + " " + (entry.detail ?: "")).contains(secretBody) ||
                (entry.message + " " + (entry.detail ?: "")).contains("tomorrow at 4 PM")
        }
        assertEquals(false, leaked)
    }

    private fun buildRealPipeline(): NotificationTaskProcessor =
        NotificationTaskProcessor(DeterministicNotificationTaskExtractor(), intake, time, zone)

    private fun ZonedDateTime(day: Int, month: Int, hour: Int, minute: Int = 0): java.time.ZonedDateTime =
        java.time.ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    companion object {
        const val DeterministicModelIdForTests = "test-extractor-v1"
    }
}
