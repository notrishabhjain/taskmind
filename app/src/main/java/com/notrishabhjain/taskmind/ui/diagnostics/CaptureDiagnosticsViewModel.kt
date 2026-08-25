package com.notrishabhjain.taskmind.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import com.notrishabhjain.taskmind.TaskMindApplication
import com.notrishabhjain.taskmind.domain.model.CaptureState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CaptureDiagnosticsViewModel(
    scheduler: com.notrishabhjain.taskmind.notification.CaptureWorkScheduler,
    stateCounts: com.notrishabhjain.taskmind.domain.repository.NotificationCaptureRepository
) : ViewModel() {

    val uiState: StateFlow<CaptureDiagnosticsUiState> = combine(
        scheduler.observeDrainWork(),
        stateCounts.observeStateCounts()
    ) { workInfos, counts ->
        CaptureDiagnosticsUiState(
            loading = false,
            workInfoAvailable = true,
            workRows = workInfos.map { it.toRow() },
            counts = CaptureState.entries.map { state ->
                StateCountRowUi(
                    state = state,
                    stateLabelRes = captureStateLabel(state),
                    count = counts[state] ?: 0
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureDiagnosticsUiState())

    private fun WorkInfo.toRow() = DrainWorkRowUi(
        id = id.toString(),
        state = state.name,
        attempts = runAttemptCount
    )

    private fun captureStateLabel(state: CaptureState): Int = when (state) {
        CaptureState.CAPTURED -> R.string.capture_state_captured
        CaptureState.QUEUED -> R.string.capture_state_queued
        CaptureState.PROCESSING -> R.string.capture_state_processing
        CaptureState.PROCESSED -> R.string.capture_state_processed
        CaptureState.REVIEWED -> R.string.capture_state_reviewed
        CaptureState.REJECTED -> R.string.capture_state_rejected
        CaptureState.FAILED -> R.string.capture_state_failed
        CaptureState.RETRY_PENDING -> R.string.capture_state_retry_pending
        CaptureState.IGNORED -> R.string.capture_state_ignored
        CaptureState.DEFERRED -> R.string.capture_state_deferred
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TaskMindApplication).container
                CaptureDiagnosticsViewModel(
                    scheduler = container.captureWorkScheduler,
                    stateCounts = container.notificationCaptureRepository
                )
            }
        }
    }
}
