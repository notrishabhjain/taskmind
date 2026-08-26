package com.notrishabhjain.taskmind.notification

/**
 * Pure-Kotlin representation of everything the ingestion boundary knows about
 * one Android notification. Framework types stop at
 * [AndroidNotificationExtractor]; every downstream component consumes this.
 */
data class MessageEntry(
    val sender: String?,
    val text: String,
    /** Original MessagingStyle timestamp in epoch millis, when provided. */
    val timestampMs: Long?,
    val historic: Boolean = false
)

data class ConversationContext(
    val title: String?,
    val isGroup: Boolean,
    val messages: List<MessageEntry>
)

enum class NotificationStyle { MESSAGING, INBOX, BIG_TEXT, MEDIA, PROGRESS, DECORATED_CUSTOM, UNKNOWN }

/** Names of populated extras only — never their values. */
data class ExtrasCensus(val populatedKeys: List<String>)

data class NotificationSnapshot(
    // Identity
    val packageName: String,
    val notificationKey: String,
    val notificationId: Int?,
    val tag: String?,
    val postTimeMs: Long,
    // Presentation
    val appLabel: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val infoText: String?,
    val summaryText: String?,
    // Messaging
    val conversation: ConversationContext?,
    // Metadata
    val category: String?,
    val channelId: String?,
    val style: NotificationStyle,
    val isOngoing: Boolean,
    val groupKey: String?,
    val flags: Int,
    val extrasCensus: ExtrasCensus
)
