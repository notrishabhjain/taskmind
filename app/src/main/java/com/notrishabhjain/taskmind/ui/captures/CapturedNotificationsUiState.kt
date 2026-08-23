package com.notrishabhjain.taskmind.ui.captures

import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture

data class CaptureRowUi(
    val id: Long,
    val sourceLabel: String,
    val title: String?,
    val preview: String?,
    val timestampLabel: String,
    val stateLabelRes: Int
)

data class CapturedNotificationsUiState(
    val loading: Boolean = true,
    val rows: List<CaptureRowUi> = emptyList()
) {
    val isEmpty: Boolean get() = !loading && rows.isEmpty()
}

fun captureStateLabelRes(state: CaptureState): Int = when (state) {
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
