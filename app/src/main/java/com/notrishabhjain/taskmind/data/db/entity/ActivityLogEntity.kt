package com.notrishabhjain.taskmind.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_log",
    indices = [Index("createdAt")]
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String,
    val message: String,
    val detail: String?,
    val taskId: Long?,
    val createdAt: Long
)
