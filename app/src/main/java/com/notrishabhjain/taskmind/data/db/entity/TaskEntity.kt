package com.notrishabhjain.taskmind.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(
            value = ["sourceType", "sourceRef", "titleKey"],
            name = "index_tasks_source_key",
            unique = true
        ),
        Index("status"),
        Index("dueAt"),
        Index("projectId")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val titleKey: String,
    val notes: String?,
    val dueAt: Long?,
    val priority: String,
    val status: String,
    val projectId: Long?,
    val recurrenceRule: String?,
    val reminderAt: Long?,
    val parentTaskId: Long?,
    val sortOrder: Int?,
    val sourceType: String,
    val sourceRef: String?,
    val sourceLabel: String?,
    val sourceApp: String?,
    val evidence: String?,
    val confidence: Double?,
    val inferenceOrigin: String?,
    val modelId: String?,
    val remoteId: String?,
    val syncState: String,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
