package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.model.Priority
import java.time.Instant

data class TaskEdit(
    val title: String,
    val notes: String?,
    val priority: Priority,
    val dueAt: Instant?
)
