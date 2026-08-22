package com.notrishabhjain.taskmind.ui.tasks

import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.TaskSort
import com.notrishabhjain.taskmind.domain.model.TaskView

data class TaskRowUi(
    val id: Long,
    val title: String,
    val completed: Boolean,
    val archived: Boolean,
    val dueLabel: String?,
    val overdue: Boolean,
    val priority: Priority
)

data class TasksUiState(
    val loading: Boolean = true,
    val view: TaskView = TaskView.TODAY,
    val sort: TaskSort = TaskSort.DUE_DATE,
    val searchQuery: String = "",
    val rows: List<TaskRowUi> = emptyList(),
    val pendingDeleteId: Long? = null
) {
    val isEmpty: Boolean get() = !loading && rows.isEmpty()
}
