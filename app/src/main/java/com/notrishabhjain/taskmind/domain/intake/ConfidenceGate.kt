package com.notrishabhjain.taskmind.domain.intake

enum class ConfidenceDecision { AUTO_CREATE, SEND_TO_REVIEW, REJECT }

data class ConfidenceThresholds(
    val autoCreateAtOrAbove: Double = DEFAULT_AUTO_CREATE,
    val rejectBelow: Double = DEFAULT_REJECT_BELOW
) {
    init {
        require(autoCreateAtOrAbove in 0.0..1.0) {
            "autoCreateAtOrAbove must be within [0, 1], was $autoCreateAtOrAbove"
        }
        require(rejectBelow in 0.0..autoCreateAtOrAbove) {
            "rejectBelow must be within [0, autoCreateAtOrAbove], was $rejectBelow"
        }
    }

    companion object {
        const val DEFAULT_AUTO_CREATE = 0.75
        const val DEFAULT_REJECT_BELOW = 0.40
    }
}

class ConfidenceGate(
    private val thresholds: ConfidenceThresholds = ConfidenceThresholds()
) {

    fun classify(confidence: Double?): ConfidenceDecision = when {
        confidence == null -> ConfidenceDecision.SEND_TO_REVIEW
        !confidence.isFinite() -> ConfidenceDecision.SEND_TO_REVIEW
        confidence < 0.0 || confidence > 1.0 -> ConfidenceDecision.SEND_TO_REVIEW
        confidence >= thresholds.autoCreateAtOrAbove -> ConfidenceDecision.AUTO_CREATE
        confidence >= thresholds.rejectBelow -> ConfidenceDecision.SEND_TO_REVIEW
        else -> ConfidenceDecision.REJECT
    }
}
