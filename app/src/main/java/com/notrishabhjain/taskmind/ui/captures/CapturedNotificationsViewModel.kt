package com.notrishabhjain.taskmind.ui.captures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.notrishabhjain.taskmind.TaskMindApplication
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import com.notrishabhjain.taskmind.domain.repository.NotificationCaptureRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CapturedNotificationsViewModel(
    private val notificationCaptureRepository: NotificationCaptureRepository,
    displayLimit: Int = DISPLAY_LIMIT,
    zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    val uiState: StateFlow<CapturedNotificationsUiState> = notificationCaptureRepository
        .observeRecentCaptures(displayLimit)
        .map { captures -> captures.map { it.toRow(zoneId) } }
        .map { rows -> CapturedNotificationsUiState(loading = false, rows = rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CapturedNotificationsUiState())

    fun observeCapture(id: Long): Flow<NotificationCapture?> =
        notificationCaptureRepository.observeCapture(id)

    private fun NotificationCapture.toRow(zone: ZoneId): CaptureRowUi = CaptureRowUi(
        id = id,
        sourceLabel = sourceAppLabel ?: sourcePackage,
        title = title ?: text?.lineSequence()?.firstOrNull(),
        preview = (text ?: canonicalSourceText).lineSequence().firstOrNull(),
        timestampLabel = createdAt.atZone(zone).format(TIMESTAMP_FORMATTER),
        stateLabelRes = captureStateLabelRes(state)
    )

    companion object {
        const val DISPLAY_LIMIT = 100

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM uuuu · HH:mm")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TaskMindApplication).container
                CapturedNotificationsViewModel(
                    notificationCaptureRepository = container.notificationCaptureRepository
                )
            }
        }
    }
}
