package com.notrishabhjain.taskmind.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["nameKey"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val nameKey: String
)
