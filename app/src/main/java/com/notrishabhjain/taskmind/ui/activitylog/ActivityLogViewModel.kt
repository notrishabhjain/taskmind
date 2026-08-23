package com.notrishabhjain.taskmind.ui.activitylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.notrishabhjain.taskmind.TaskMindApplication
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ActivityLogViewModel(
    activityLogRepository: ActivityLogRepository,
    displayLimit: Int = ActivityLogUiState.DISPLAY_LIMIT,
    zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    val uiState: StateFlow<ActivityLogUiState> = activityLogRepository
        .observeRecent(displayLimit)
        .map { entries -> entries.map { it.toRow(zoneId) } }
        .map { rows -> ActivityLogUiState(loading = false, rows = rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityLogUiState())

    private fun ActivityLogEntry.toRow(zone: ZoneId): ActivityRowUi = ActivityRowUi(
        id = id,
        timestampLabel = createdAt.atZone(zone).format(TIMESTAMP_FORMATTER),
        categoryLabelRes = categoryLabelRes(category),
        message = message,
        detail = detail,
        taskId = taskId
    )

    companion object {
        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM uuuu · HH:mm")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TaskMindApplication).container
                ActivityLogViewModel(
                    activityLogRepository = container.activityLogRepository
                )
            }
        }
    }
}
