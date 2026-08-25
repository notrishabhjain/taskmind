package com.notrishabhjain.taskmind.ui.diagnostics

import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.domain.model.CaptureState

data class DrainWorkRowUi(
    val id: String,
    val state: String,
    val attempts: Int
)

data class StateCountRowUi(
    val state: CaptureState,
    val stateLabelRes: Int,
    val count: Int
)

data class CaptureDiagnosticsUiState(
    val loading: Boolean = true,
    val workRows: List<DrainWorkRowUi> = emptyList(),
    val workInfoAvailable: Boolean = false,
    val counts: List<StateCountRowUi> = emptyList()
) {
    val isEmptyWork: Boolean get() = workInfoAvailable && workRows.isEmpty()
}
