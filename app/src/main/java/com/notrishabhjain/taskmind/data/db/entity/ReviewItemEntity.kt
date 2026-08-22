package com.notrishabhjain.taskmind.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_items",
    indices = [
        Index("status"),
        Index("createdAt"),
        Index(value = ["sourceType", "sourceRef", "titleKey"])
    ]
)
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayTitle: String,
    val titleKey: String,
    val notes: String?,
    val dueAt: Long?,
    val priority: String,
    val projectId: Long?,
    val sourceType: String,
    val sourceRef: String,
    val sourceLabel: String?,
    val sourceApp: String?,
    val sourceText: String?,
    val evidence: String?,
    val reasoning: String?,
    val confidence: Double?,
    val status: String,
    val resultingTaskId: Long?,
    val createdAt: Long,
    val decidedAt: Long?
)
