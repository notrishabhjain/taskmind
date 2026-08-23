package com.notrishabhjain.taskmind.ui.review

import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.ReviewItem

data class ReviewRowUi(
    val id: Long,
    val title: String,
    val priority: Priority,
    val confidencePercent: Int?,
    val dueLabel: String?,
    val evidence: String?,
    val reasoning: String?,
    val sourceSummary: String,
    val sourceText: String?
)

data class ReviewUiState(
    val loading: Boolean = true,
    val rows: List<ReviewRowUi> = emptyList(),
    val busyItemId: Long? = null,
    val messageRes: Int? = null
) {
    val isEmpty: Boolean get() = !loading && rows.isEmpty()
}
