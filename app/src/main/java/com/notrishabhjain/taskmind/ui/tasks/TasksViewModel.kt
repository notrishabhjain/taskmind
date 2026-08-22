package com.notrishabhjain.taskmind.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.notrishabhjain.taskmind.TaskMindApplication
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskQuery
import com.notrishabhjain.taskmind.domain.model.TaskSort
import com.notrishabhjain.taskmind.domain.model.TaskStatus
import com.notrishabhjain.taskmind.domain.model.TaskTimeWindows
import com.notrishabhjain.taskmind.domain.model.TaskView
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import com.notrishabhjain.taskmind.domain.service.TaskService
import com.notrishabhjain.taskmind.domain.time.TimeProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val taskService: TaskService,
    private val timeProvider: TimeProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val query = MutableStateFlow(TaskQuery())
    private val pendingDeleteId = MutableStateFlow<Long?>(null)
    private val refreshTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            while (true) {
                delay(RESYNC_INTERVAL_MS)
                refreshTick.update { it + 1 }
            }
        }
    }

    fun onHostResumed() {
        refreshTick.update { it + 1 }
    }

    private val rowsState: StateFlow<TasksUiState> =
        combine(query, refreshTick) { current, _ -> current }
            .flatMapLatest { current ->
                val window = TaskTimeWindows.dayBounds(timeProvider.now(), zoneId)
                taskRepository.observeTasks(current, window.start, window.end)
                    .map { tasks ->
                        TasksUiState(
                            loading = false,
                            view = current.view,
                            sort = current.sort,
                            searchQuery = current.search,
                            rows = tasks.map { it.toRow() }
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    val uiState: StateFlow<TasksUiState> =
        combine(rowsState, pendingDeleteId) { state, deleteId ->
            state.copy(pendingDeleteId = deleteId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun onViewSelected(view: TaskView) = query.update { it.copy(view = view) }

    fun onSortSelected(sort: TaskSort) = query.update { it.copy(sort = sort) }

    fun onSearchChanged(text: String) = query.update { it.copy(search = text) }

    fun onSearchClosed() = query.update { it.copy(search = "") }

    fun onToggleComplete(row: TaskRowUi) {
        viewModelScope.launch {
            if (row.completed) taskService.reopen(row.id) else taskService.complete(row.id)
        }
    }

    fun onArchiveToggled(row: TaskRowUi) {
        viewModelScope.launch {
            if (row.archived) taskService.unarchive(row.id) else taskService.archive(row.id)
        }
    }

    fun onDeleteRequested(taskId: Long) {
        pendingDeleteId.value = taskId
    }

    fun onDeleteDismissed() {
        pendingDeleteId.value = null
    }

    fun onDeleteConfirmed() {
        val id = pendingDeleteId.value ?: return
        pendingDeleteId.value = null
        viewModelScope.launch { taskService.delete(id) }
    }

    private fun Task.toRow(): TaskRowUi {
        val now = timeProvider.now()
        return TaskRowUi(
            id = id,
            title = title,
            completed = status == TaskStatus.COMPLETED,
            archived = status == TaskStatus.ARCHIVED,
            dueLabel = dueAt?.let { formatDue(it, now) },
            overdue = dueAt != null && status == TaskStatus.ACTIVE && dueAt.isBefore(now),
            priority = priority
        )
    }

    private fun formatDue(instant: Instant, now: Instant): String {
        val zoned = instant.atZone(zoneId)
        val formatter = if (zoned.toLocalDate() == now.atZone(zoneId).toLocalDate()) {
            DateTimeFormatter.ofPattern("'Today' · h:mm a")
        } else {
            DateTimeFormatter.ofPattern("d MMM uuuu · h:mm a")
        }
        return zoned.format(formatter)
    }

    companion object {
        const val RESYNC_INTERVAL_MS = 15L * 60L * 1_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TaskMindApplication).container
                TasksViewModel(
                    taskRepository = container.taskRepository,
                    taskService = container.taskService,
                    timeProvider = container.timeProvider
                )
            }
        }
    }
}
