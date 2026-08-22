package com.notrishabhjain.taskmind.domain.intake

import com.notrishabhjain.taskmind.domain.model.InferenceOrigin
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.SourceType
import java.time.Instant

enum class ProposalOrigin { MANUAL, EXTRACTION, REVIEW_ACCEPTANCE }

data class TaskProposal(
    val origin: ProposalOrigin,
    val sourceType: SourceType,
    val sourceRef: String?,
    val title: String,
    val notes: String? = null,
    val dueAt: Instant? = null,
    val priority: Priority = Priority.MEDIUM,
    val projectId: Long? = null,
    val tagNames: List<String> = emptyList(),
    val sourceLabel: String? = null,
    val sourceApp: String? = null,
    val sourceText: String? = null,
    val evidence: String? = null,
    val reasoning: String? = null,
    val confidence: Double? = null,
    val inferenceOrigin: InferenceOrigin? = null,
    val modelId: String? = null,
    val originReviewItemId: Long? = null
) {
    companion object {

        fun manual(
            title: String,
            notes: String? = null,
            dueAt: Instant? = null,
            priority: Priority = Priority.MEDIUM,
            projectId: Long? = null,
            tagNames: List<String> = emptyList()
        ): TaskProposal = TaskProposal(
            origin = ProposalOrigin.MANUAL,
            sourceType = SourceType.MANUAL,
            sourceRef = null,
            title = title,
            notes = notes,
            dueAt = dueAt,
            priority = priority,
            projectId = projectId,
            tagNames = tagNames,
            confidence = null,
            inferenceOrigin = InferenceOrigin.HUMAN_INPUT
        )

        fun extracted(
            sourceType: SourceType,
            sourceRef: String,
            title: String,
            confidence: Double?,
            evidence: String?,
            sourceText: String,
            notes: String? = null,
            dueAt: Instant? = null,
            priority: Priority = Priority.MEDIUM,
            sourceLabel: String? = null,
            sourceApp: String? = null,
            reasoning: String? = null,
            modelId: String? = null
        ): TaskProposal {
            require(sourceType == SourceType.NOTIFICATION || sourceType == SourceType.CALL) {
                "Extraction proposals must originate from NOTIFICATION or CALL, was $sourceType"
            }
            return TaskProposal(
                origin = ProposalOrigin.EXTRACTION,
                sourceType = sourceType,
                sourceRef = sourceRef,
                title = title,
                notes = notes,
                dueAt = dueAt,
                priority = priority,
                sourceLabel = sourceLabel,
                sourceApp = sourceApp,
                sourceText = sourceText,
                evidence = evidence,
                reasoning = reasoning,
                confidence = confidence,
                modelId = modelId,
                inferenceOrigin = InferenceOrigin.AUTOMATIC_INFERENCE
            )
        }

        fun fromAcceptedReview(item: ReviewItem): TaskProposal = TaskProposal(
            origin = ProposalOrigin.REVIEW_ACCEPTANCE,
            sourceType = item.sourceType,
            sourceRef = item.sourceRef,
            title = item.displayTitle,
            notes = item.notes,
            dueAt = item.dueAt,
            priority = item.priority,
            projectId = item.projectId,
            sourceLabel = item.sourceLabel,
            sourceApp = item.sourceApp,
            sourceText = item.sourceText,
            evidence = item.evidence,
            reasoning = item.reasoning,
            confidence = 1.0,
            inferenceOrigin = InferenceOrigin.REVIEW_CONFIRMATION,
            originReviewItemId = item.id
        )
    }
}
