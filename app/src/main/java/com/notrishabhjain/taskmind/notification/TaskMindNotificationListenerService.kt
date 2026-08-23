package com.notrishabhjain.taskmind.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.notrishabhjain.taskmind.di.AppContainer
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TaskMindNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = (applicationContext as com.notrishabhjain.taskmind.TaskMindApplication).container
    }

    override fun onDestroy() {
        container.notificationListenerConnected.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        container.notificationListenerConnected.value = true
    }

    override fun onListenerDisconnected() {
        container.notificationListenerConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val capture = captureOrNull(sbn) ?: return
        scope.launch {
            try {
                container.notificationCaptureRepository.insertIfAbsent(capture)
            } catch (e: Exception) {
                logGenericFailure(e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Removals are intentionally not persisted in 4B.
    }

    private fun captureOrNull(sbn: StatusBarNotification): com.notrishabhjain.taskmind.domain.model.NotificationCapture? {
        val ownPackage = applicationContext.packageName
        val sourcePackage = sbn.packageName ?: return null
        if (NotificationCanonicalizer.isSelfNotification(sourcePackage, ownPackage)) return null

        val notification = sbn.notification ?: return null
        val extras = notification.extras
        fun extra(key: String): String? = extras?.getCharSequence(key)?.toString()

        val input = RawNotificationInput(
            sourcePackage = sourcePackage,
            notificationKey = sbn.key ?: buildString {
                append(sourcePackage); append('|'); append(sbn.id); append('|'); append(sbn.tag)
            },
            postTimeMillis = sbn.postTime,
            title = extra(Notification.EXTRA_TITLE),
            text = extra(Notification.EXTRA_TEXT),
            bigText = extra(Notification.EXTRA_BIG_TEXT),
            subText = extra(Notification.EXTRA_SUB_TEXT),
            infoText = extra(Notification.EXTRA_INFO_TEXT),
            conversationTitle = extra(Notification.EXTRA_CONVERSATION_TITLE),
            category = notification.category,
            appLabel = resolveAppLabel(sourcePackage)
        )

        return NotificationCanonicalizer.toCapture(
            input,
            capturedAt = container.timeProvider.now()
        )
    }

    private fun resolveAppLabel(sourcePackage: String): String? = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(sourcePackage, 0)
        )?.toString()
    } catch (e: Exception) {
        null
    }

    private fun logGenericFailure(e: Exception) {
        scope.launch {
            runCatching {
                container.activityLogRepository.append(
                    com.notrishabhjain.taskmind.domain.model.ActivityLogEntry(
                        category = ActivityCategory.PROCESSING_FAILED,
                        message = "A notification could not be captured",
                        detail = "${e::class.simpleName}: ${e.message}",
                        taskId = null,
                        createdAt = container.timeProvider.now()
                    )
                )
            }
        }
    }
}
