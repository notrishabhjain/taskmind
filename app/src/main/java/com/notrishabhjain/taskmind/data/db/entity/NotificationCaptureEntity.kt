package com.notrishabhjain.taskmind.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_captures",
    indices = [
        Index(
            value = ["idempotencyKey"],
            name = "index_notification_captures_idempotency",
            unique = true
        ),
        Index(value = ["state"], name = "index_notification_captures_state"),
        Index(value = ["sourceRef"], name = "index_notification_captures_sourceRef"),
        Index(value = ["createdAt"], name = "index_notification_captures_createdAt"),
        Index(value = ["updatedAt"], name = "index_notification_captures_updatedAt")
    ]
)
data class NotificationCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val idempotencyKey: String,
    val sourcePackage: String,
    val sourceAppLabel: String?,
    val notificationKey: String,
    val notificationId: Int?,
    val notificationTag: String?,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val infoText: String?,
    val conversationTitle: String?,
    val category: String?,
    val channelLabel: String?,
    val canonicalSourceText: String,
    val contentHash: String,
    val sourceRef: String,
    val state: String,
    val retryCount: Int,
    val lastError: String?,
    val resultingTaskId: Long?,
    val processedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
