package com.notrishabhjain.taskmind.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.TaskMindApplication
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.repository.ReviewRepository
import com.notrishabhjain.taskmind.domain.service.ReviewDecisionResult
import com.notrishabhjain.taskmind.domain.service.ReviewService
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReviewInboxViewModel(
    reviewRepository: ReviewRepository,
    private val reviewService: ReviewService,
    timeProvider: TimeProvider,
    zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val busyItemId = MutableStateFlow<Long?>(null)
    private val messageRes = MutableStateFlow<Int?>(null)

    private val rowsState: StateFlow<ReviewUiState> = reviewRepository
        .observePending()
        .map { items -> items.map { it.toRow(zoneId) } }
        .map { rows -> ReviewUiState(loading = false, rows = rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    val uiState: StateFlow<ReviewUiState> =
        combine(rowsState, busyItemId, messageRes) { state, busy, message ->
            state.copy(busyItemId = busy, messageRes = message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun onAcceptClicked(row: ReviewRowUi) = decide(row) { reviewService.accept(it) }

    fun onDismissClicked(row: ReviewRowUi) = decide(row) { reviewService.dismiss(it) }

    fun onMessageShown() {
        messageRes.value = null
    }

    private fun decide(row: ReviewRowUi, decision: suspend (Long) -> ReviewDecisionResult) {
        viewModelScope.launch {
            busyItemId.value = row.id
            when (decision(row.id)) {
                is ReviewDecisionResult.Accepted,
                ReviewDecisionResult.Dismissed -> Unit

                ReviewDecisionResult.DuplicateOfExistingTask ->
                    messageRes.value = R.string.review_duplicate_message

                ReviewDecisionResult.AlreadyDecided ->
                    messageRes.value = R.string.review_unavailable_message

                ReviewDecisionResult.Missing ->
                    messageRes.value = R.string.review_unavailable_message

                is ReviewDecisionResult.Failed ->
                    messageRes.value = R.string.review_decision_failed
            }
            busyItemId.value = null
        }
    }

    private fun ReviewItem.toRow(zone: ZoneId): ReviewRowUi = ReviewRowUi(
        id = id,
        title = displayTitle,
        priority = priority,
        confidencePercent = confidence?.let { (it * 100).toInt().coerceIn(0, 100) },
        dueLabel = dueAt?.atZone(zone)?.format(DUE_FORMATTER),
        evidence = evidence,
        reasoning = reasoning,
        sourceSummary = buildString {
            append(sourceType.name.lowercase().replace('_', ' '))
            sourceApp?.let { append(" · ").append(it) }
            append(" · ").append(sourceRef)
        },
        sourceText = sourceText
    )

    companion object {
        private val DUE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM uuuu · h:mm a")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TaskMindApplication).container
                ReviewInboxViewModel(
                    reviewRepository = container.reviewRepository,
                    reviewService = container.reviewService,
                    timeProvider = container.timeProvider
                )
            }
        }
    }
}
