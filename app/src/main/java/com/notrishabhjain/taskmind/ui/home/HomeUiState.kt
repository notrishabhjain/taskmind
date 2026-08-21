package com.notrishabhjain.taskmind.ui.home

data class HomeUiState(
    val taskCount: Int = 0
) {
    val hasNoTasks: Boolean
        get() = taskCount == 0

    init {
        require(taskCount >= 0) { "taskCount must be non-negative, was $taskCount" }
    }
}
