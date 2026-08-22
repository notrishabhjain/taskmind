package com.notrishabhjain.taskmind.domain.intake

import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.model.InferenceOrigin
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskProposal
import com.notrishabhjain.taskmind.domain.model.TaskStatus
import com.notrishabhjain.taskmind.domain.model.SyncState
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.repository.DuplicateTaskException
import com.notrishabhjain.taskmind.domain.repository.ProjectTagRepository
import com.notrishabhjain.taskmind.domain.repository.ReviewRepository
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.time.Instant

private val VALIDATION_EXEMPT_ORIGINS = setOf(
    ProposalOrigin.MANUAL,
    ProposalOrigin.REVIEW_ACCEPTANCE
)

internal fun originRequiresGeneratedValidation(origin: ProposalOrigin): Boolean =
    origin !in VALIDATION_EXEMPT_ORIGINS

class TaskIntakeService(
    private val taskRepository: TaskRepository,
    private val reviewRepository: ReviewRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val projectTagRepository: ProjectTagRepository,
    private val timeProvider: TimeProvider,
    private val confidenceGate: ConfidenceGate = ConfidenceGate()
) {

    suspend fun submit(proposal: TaskProposal): IntakeOutcome {
        val now = timeProvider.now()

        val displayTitle = TitleNormalizer.normalize(proposal.title)
        if (displayTitle.isBlank()) {
            return reject(RejectionReason.BLANK_TITLE, "Title is blank or whitespace-only", now)
        }

        if (proposal.origin != ProposalOrigin.MANUAL && proposal.sourceRef.isNullOrBlank()) {
            return reject(
                RejectionReason.MISSING_SOURCE_REF,
                "Origin ${proposal.origin.name} requires a non-blank source reference",
                now
            )
        }

        val titleKey = TitleNormalizer.titleKey(proposal.title)

        if (originRequiresGeneratedValidation(proposal.origin)) {
            when (confidenceGate.classify(proposal.confidence)) {
                ConfidenceDecision.REJECT -> return reject(
                    RejectionReason.BELOW_CONFIDENCE_THRESHOLD,
                    "Confidence ${proposal.confidence} is below the rejection threshold",
                    now
                )

                ConfidenceDecision.SEND_TO_REVIEW ->
                    return routeToReview(proposal, displayTitle, titleKey, now)

                ConfidenceDecision.AUTO_CREATE -> Unit
            }

            val evidenceCheck = EvidenceValidator.validate(proposal.evidence, proposal.sourceText)
            if (evidenceCheck !is EvidenceCheck.Valid) {
                activityLogRepository.append(
                    entry(
                        category = ActivityCategory.EVIDENCE_VALIDATION_FAILED,
                        message = "Evidence for \"$displayTitle\" could not be verified in the original message",
                        detail = describe(evidenceCheck),
                        taskId = null,
                        at = now
                    )
                )
                return IntakeOutcome.Rejected(RejectionReason.EVIDENCE_INVALID)
            }
        }

        proposal.sourceRef
            ?.takeIf { it.isNotBlank() }
            ?.let { sourceRef ->
                taskRepository.findByLogicalKey(proposal.sourceType, sourceRef, titleKey)
            }
            ?.let { existing ->
                dismissOriginalReviewIfPresent(proposal, existing.id, now)
                activityLogRepository.append(
                    entry(
                        category = ActivityCategory.DUPLICATE_DETECTED,
                        message = "Duplicate ignored for \"$displayTitle\"",
                        detail = "Matches existing task #${existing.id}",
                        taskId = existing.id,
                        at = now
                    )
                )
                return IntakeOutcome.DuplicateDetected(existing.id, titleKey)
            }

        val tagIds = projectTagRepository.ensureTags(proposal.tagNames)

        val task = Task(
            title = displayTitle,
            titleKey = titleKey,
            notes = proposal.notes?.trim()?.ifBlank { null },
            dueAt = proposal.dueAt,
            priority = proposal.priority,
            projectId = proposal.projectId,
            tagIds = tagIds,
            sourceType = proposal.sourceType,
            sourceRef = proposal.sourceRef,
            sourceLabel = proposal.sourceLabel,
            sourceApp = proposal.sourceApp,
            evidence = proposal.evidence?.trim(),
            confidence = proposal.confidence,
            inferenceOrigin = proposal.inferenceOrigin,
            modelId = proposal.modelId,
            syncState = SyncState.LOCAL_ONLY,
            status = TaskStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )

        val stored = try {
            taskRepository.insert(task)
        } catch (duplicate: DuplicateTaskException) {
            val existing = taskRepository.findByLogicalKey(
                sourceType = duplicate.sourceType,
                sourceRef = duplicate.sourceRef,
                titleKey = duplicate.titleKey
            )
            if (existing == null) {
                activityLogRepository.append(
                    entry(
                        category = ActivityCategory.PROCESSING_FAILED,
                        message = "Could not persist \"$displayTitle\"",
                        detail = "A duplicate-key collision was reported but no matching task exists",
                        taskId = null,
                        at = now
                    )
                )
                return IntakeOutcome.Failed("duplicate-collision-without-existing-task")
            }
            dismissOriginalReviewIfPresent(proposal, existing.id, now)
            activityLogRepository.append(
                entry(
                    category = ActivityCategory.DUPLICATE_DETECTED,
                    message = "Duplicate ignored for \"$displayTitle\"",
                    detail = "Resolved after concurrent insert; matches existing task #${existing.id}",
                    taskId = existing.id,
                    at = now
                )
            )
            return IntakeOutcome.DuplicateDetected(existing.id, titleKey)
        }

        if (proposal.origin == ProposalOrigin.REVIEW_ACCEPTANCE) {
            proposal.originReviewItemId?.let { reviewId ->
                reviewRepository.markDecided(reviewId, ReviewStatus.ACCEPTED, stored.id, now)
            }
        }

        activityLogRepository.append(
            entry(
                category = ActivityCategory.TASK_CREATED,
                message = "Task created: \"${stored.title}\"",
                detail = provenanceDetail(stored),
                taskId = stored.id,
                at = now
            )
        )

        return IntakeOutcome.Created(stored)
    }

    private suspend fun routeToReview(
        proposal: TaskProposal,
        displayTitle: String,
        titleKey: String,
        now: Instant
    ): IntakeOutcome.RoutedToReview {
        val item = ReviewItem(
            displayTitle = displayTitle,
            titleKey = titleKey,
            notes = proposal.notes?.trim()?.ifBlank { null },
            dueAt = proposal.dueAt,
            priority = proposal.priority,
            projectId = proposal.projectId,
            sourceType = proposal.sourceType,
            sourceRef = requireNotNull(proposal.sourceRef),
            sourceLabel = proposal.sourceLabel,
            sourceApp = proposal.sourceApp,
            sourceText = proposal.sourceText,
            evidence = proposal.evidence,
            reasoning = proposal.reasoning,
            confidence = proposal.confidence,
            status = ReviewStatus.PENDING,
            createdAt = now
        )
        val saved = reviewRepository.insert(item)
        activityLogRepository.append(
            entry(
                category = ActivityCategory.INTAKE_SENT_TO_REVIEW,
                message = "\"$displayTitle\" needs your confirmation",
                detail = confidenceDetail(proposal.confidence),
                taskId = null,
                at = now
            )
        )
        return IntakeOutcome.RoutedToReview(saved)
    }

    private suspend fun reject(reason: RejectionReason, detail: String, now: Instant): IntakeOutcome.Rejected {
        activityLogRepository.append(
            entry(
                category = ActivityCategory.INTAKE_REJECTED,
                message = "Proposed task was rejected",
                detail = "${reason.name}: $detail",
                taskId = null,
                at = now
            )
        )
        return IntakeOutcome.Rejected(reason)
    }

    private suspend fun dismissOriginalReviewIfPresent(proposal: TaskProposal, existingTaskId: Long, now: Instant) {
        val reviewId = proposal.originReviewItemId ?: return
        reviewRepository.markDecided(reviewId, ReviewStatus.DISMISSED, existingTaskId, now)
    }

    private fun describe(check: EvidenceCheck): String = when (check) {
        is EvidenceCheck.Valid -> "evidence verified"
        is EvidenceCheck.EmptyEvidence -> "evidence was empty or blank"
        is EvidenceCheck.MissingSource -> "original source text was unavailable"
        is EvidenceCheck.NotInSource -> "evidence \"${check.normalizedEvidence}\" does not occur in the source text"
    }

    private fun provenanceDetail(task: Task): String = buildString {
        append("source=")
        append(task.sourceType.name)
        task.sourceRef?.let { append(" ref=").append(it) }
        task.inferenceOrigin?.let { append(" origin=").append(it.name) }
        task.confidence?.let { append(" confidence=").append(it) }
        task.evidence?.let { append(" evidence=\"").append(it).append("\"") }
    }

    private fun confidenceDetail(confidence: Double?): String =
        if (confidence == null) "confidence missing" else "confidence=$confidence"

    private fun entry(
        category: ActivityCategory,
        message: String,
        detail: String?,
        taskId: Long?,
        at: Instant
    ): ActivityLogEntry = ActivityLogEntry(
        category = category,
        message = message,
        detail = detail,
        taskId = taskId,
        createdAt = at
    )
}
