package com.notrishabhjain.taskmind.notification

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification

/**
 * The single Android-framework boundary of the ingestion pipeline:
 * StatusBarNotification -> NotificationSnapshot. Nothing downstream touches
 * framework notification classes.
 *
 * API notes (minSdk 26):
 * - channelId is available from API 26.
 * - EXTRA_MESSAGES / EXTRA_HISTORIC_MESSAGES / EXTRA_TEMPLATE /
 *   EXTRA_IS_GROUP_CONVERSATION are read as raw extras; their Bundle payload
 *   uses the stable MessagingStyle.Message parcel keys ("text", "sender",
 *   "time"), parsed defensively.
 * - EXTRA_MESSAGING_PERSON (API 28+) is intentionally not dereferenced; its
 *   presence shows up only in the extras census key names when populated.
 */
class AndroidNotificationExtractor(private val appContext: Context) {

    fun extract(sbn: StatusBarNotification): NotificationSnapshot {
        val notification = sbn.notification
        val extras = notification?.extras

        fun text(key: String): String? = extras?.getCharSequence(key)?.toString()

        val messages = parseMessageBundles(extras?.getBundleArray(Notification.EXTRA_MESSAGES), historic = false)
        val historicMessages = parseMessageBundles(extras?.getBundleArray(Notification.EXTRA_HISTORIC_MESSAGES), historic = true)

        val conversationTitle = text(Notification.EXTRA_CONVERSATION_TITLE)
        val conversation = if (messages.isNotEmpty() || historicMessages.isNotEmpty()) {
            ConversationContext(
                title = conversationTitle,
                isGroup = extras?.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) == true,
                messages = historicMessages + messages
            )
        } else {
            null
        }

        val style = resolveStyle(extras?.getString(Notification.EXTRA_TEMPLATE), messages, historicMessages)

        return NotificationSnapshot(
            packageName = sbn.packageName ?: "",
            notificationKey = sbn.key ?: fallbackKey(sbn),
            notificationId = sbn.id,
            tag = sbn.tag,
            postTimeMs = sbn.postTime,
            appLabel = resolveAppLabel(sbn.packageName),
            title = text(Notification.EXTRA_TITLE),
            text = text(Notification.EXTRA_TEXT),
            bigText = text(Notification.EXTRA_BIG_TEXT),
            subText = text(Notification.EXTRA_SUB_TEXT),
            infoText = text(Notification.EXTRA_INFO_TEXT),
            summaryText = text(Notification.EXTRA_SUMMARY_TEXT),
            conversation = conversation,
            category = notification?.category,
            channelId = notification?.channelId,
            style = style,
            isOngoing = notification != null &&
                (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            groupKey = notification?.group,
            flags = notification?.flags ?: 0,
            extrasCensus = ExtrasCensus(
                populatedKeys = extras?.keySet()
                    .orEmpty()
                    .filter { key -> !extras!!.get(key).let { it == null } }
                    .sorted()
            )
        )
    }

    private fun parseMessageBundles(
        bundles: Array<android.os.Bundle>?,
        historic: Boolean
    ): List<MessageEntry> {
        if (bundles == null) return emptyList()
        return bundles.mapNotNull { bundle ->
            val text = bundle.getString(MESSAGE_KEY_TEXT)?.trim() ?: return@mapNotNull null
            MessageEntry(
                sender = bundle.getString(MESSAGE_KEY_SENDER)?.trim()?.ifBlank { null },
                text = text,
                timestampMs = if (bundle.containsKey(MESSAGE_KEY_TIME)) bundle.getLong(MESSAGE_KEY_TIME) else null,
                historic = historic
            )
        }.filter { it.text.isNotEmpty() }
    }

    private fun resolveStyle(
        template: String?,
        messages: List<MessageEntry>,
        historicMessages: List<MessageEntry>
    ): NotificationStyle = when {
        template?.contains("MessagingTemplate", ignoreCase = true) == true ||
            template?.contains("MessagingStyle", ignoreCase = true) == true ||
            messages.isNotEmpty() || historicMessages.isNotEmpty() -> NotificationStyle.MESSAGING

        template?.contains("Inbox", ignoreCase = true) == true -> NotificationStyle.INBOX
        template?.contains("BigText", ignoreCase = true) == true -> NotificationStyle.BIG_TEXT
        template?.contains("Media", ignoreCase = true) == true -> NotificationStyle.MEDIA
        template?.contains("Progress", ignoreCase = true) == true -> NotificationStyle.PROGRESS
        template?.contains("DecoratedCustomView", ignoreCase = true) == true ->
            NotificationStyle.DECORATED_CUSTOM

        else -> NotificationStyle.UNKNOWN
    }

    private fun fallbackKey(sbn: StatusBarNotification): String =
        "${sbn.packageName}|${sbn.id}|${sbn.tag}"

    private fun resolveAppLabel(sourcePackage: String?): String? {
        if (sourcePackage == null) return null
        return try {
            appContext.packageManager
                .getApplicationLabel(appContext.packageManager.getApplicationInfo(sourcePackage, 0))
                ?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MESSAGE_KEY_TEXT = "text"
        const val MESSAGE_KEY_SENDER = "sender"
        const val MESSAGE_KEY_TIME = "time"
    }
}
