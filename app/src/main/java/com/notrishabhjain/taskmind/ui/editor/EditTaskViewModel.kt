package com.notrishabhjain.taskmind.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.TaskMindApplication
import com.notrishabhjain.taskmind.domain.intake.IntakeOutcome
import com.notrishabhjain.taskmind.domain.intake.RejectionReason
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.intake.TitleNormalizer
import com.notrishabhjain.taskmind.domain.intake.TaskProposal
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.repository.ProjectTagRepository
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import com.notrishabhjain.taskmind.domain.service.TaskEdit
import com.notrishabhjain.taskmind.domain.service.TaskService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditTaskViewModel(
    private val editingTaskId: Long?,
    private val taskRepository: TaskRepository,
    private val taskService: TaskService,
    private val intakeService: TaskIntakeService,
    private val projectTagRepository: ProjectTagRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val title: String = "",
        val notes: String = "",
        val priority: Priority = Priority.MEDIUM,
        val dueDate: LocalDate? = null,
        val dueTime: LocalTime? = null,
        val projectName: String = "",
        val tagsInput: String = "",
        val titleError: Boolean = false,
        val saving: Boolean = false,
        val savedAndClosed: Boolean = false,
        val saveErrorRes: Int? = null
    )

    private val state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = state.asStateFlow()

    init {
        if (editingTaskId == null) {
            state.update { it.copy(loading = false) }
        } else {
            viewModelScope.launch {
                val task = taskRepository.findById(editingTaskId)
                state.update { current ->
                    current.copy(
                        loading = false,
                        title = task?.title ?: "",
                        notes = task?.notes ?: "",
                        priority = task?.priority ?: Priority.MEDIUM,
                        dueDate = task?.dueAt?.atZone(zoneId)?.toLocalDate(),
                        dueTime = task?.dueAt?.atZone(zoneId)?.toLocalTime()
                    )
                }
            }
        }
    }

    fun onTitleChanged(value: String) {
        state.update { it.copy(title = value, titleError = false) }
    }

    fun onNotesChanged(value: String) = state.update { it.copy(notes = value) }

    fun onPriorityChanged(priority: Priority) = state.update { it.copy(priority = priority) }

    fun onDateSelected(date: LocalDate) = state.update { it.copy(dueDate = date) }

    fun onTimeSelected(time: LocalTime) = state.update { it.copy(dueTime = time) }

    fun onDueDateCleared() = state.update { it.copy(dueDate = null, dueTime = null) }

    fun onDueTimeCleared() = state.update { it.copy(dueTime = null) }

    fun onProjectNameChanged(value: String) = state.update { it.copy(projectName = value) }

    fun onTagsChanged(value: String) = state.update { it.copy(tagsInput = value) }

    fun onErrorMessageShown() = state.update { it.copy(saveErrorRes = null) }

    fun save() {
        val current = state.value
        if (current.saving || TitleNormalizer.normalize(current.title).isBlank()) {
            state.update { it.copy(titleError = true) }
            return
        }
        state.update { it.copy(saving = true, titleError = false, saveErrorRes = null) }
        viewModelScope.launch {
            val success = try {
                if (editingTaskId == null) createTask(current) else updateTask(current)
            } catch (e: Exception) {
                state.update { it.copy(saveErrorRes = R.string.editor_save_failed) }
                false
            }
            state.update { it.copy(saving = false, savedAndClosed = success) }
        }
    }

    private suspend fun createTask(current: UiState): Boolean {
        val projectId = current.projectName.trim().takeIf { it.isNotEmpty() }
            ?.let { name -> projectTagRepository.ensureProject(name).takeIf { id -> id != 0L } }
        val outcome = intakeService.submit(
            TaskProposal.manual(
                title = current.title,
                notes = current.notes.takeIf { it.isNotBlank() },
                dueAt = combineDueInstant(current),
                priority = current.priority,
                projectId = projectId,
                tagNames = splitTags(current.tagsInput)
            )
        )
        return when (outcome) {
            is IntakeOutcome.Created -> true
            is IntakeOutcome.Rejected -> {
                if (outcome.reason == RejectionReason.BLANK_TITLE) {
                    state.update { it.copy(titleError = true) }
                }
                false
            }

            is IntakeOutcome.Failed -> saveFailed()
            else -> saveFailed()
        }
    }

    private fun saveFailed(): Boolean {
        state.update { it.copy(saveErrorRes = R.string.editor_save_failed) }
        return false
    }

    private suspend fun updateTask(current: UiState): Boolean {
        val taskId = requireNotNull(editingTaskId)
        return taskService.update(
            taskId = taskId,
            edit = TaskEdit(
                title = current.title,
                notes = current.notes.takeIf { it.isNotBlank() },
                priority = current.priority,
                dueAt = combineDueInstant(current)
            )
        )
    }

    private fun combineDueInstant(current: UiState): Instant? {
        val date = current.dueDate ?: return null
        val time = current.dueTime ?: DEFAULT_DUE_TIME
        return date.atTime(time).atZone(zoneId).toInstant()
    }

    private fun splitTags(input: String): List<String> =
        input.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(9, 0)

        const val KEY_TASK_ID = "taskId"

        fun factory(taskId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val savedStateHandle = createSavedStateHandle()
                val container = (this[APPLICATION_KEY] as TaskMindApplication).container
                EditTaskViewModel(
                    editingTaskId = taskId ?: savedStateHandle[KEY_TASK_ID],
                    taskRepository = container.taskRepository,
                    taskService = container.taskService,
                    intakeService = container.taskIntakeService,
                    projectTagRepository = container.projectTagRepository
                )
            }
        }
    }
}
