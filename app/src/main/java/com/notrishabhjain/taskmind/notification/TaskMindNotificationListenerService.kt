package com.notrishabhjain.taskmind.notification

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notrishabhjain.taskmind.di.AppContainer
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.repository.CaptureInsertOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TaskMindNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var container: AppContainer

    private lateinit var captureFilter: CaptureFilter

    private var postedCount = 0

    override fun onCreate() {
        super.onCreate()
        container = (applicationContext as com.notrishabhjain.taskmind.TaskMindApplication).container
        captureFilter = CaptureFilter(
            NotificationCapturePolicy(selfPackage = packageName)
        )
        Log.i(TAG, "service onCreate")
    }

    override fun onDestroy() {
        container.notificationListenerConnected.value = false
        scope.cancel()
        Log.i(TAG, "service onDestroy")
        super.onDestroy()
    }

    override fun onListenerConnected() {
        container.notificationListenerConnected.value = true
        Log.i(TAG, "onListenerConnected")
    }

    override fun onListenerDisconnected(reason: Int) {
        container.notificationListenerConnected.value = false
        Log.i(TAG, "onListenerDisconnected reason=$reason -> requesting rebind")
        requestRebind(ComponentName(this, TaskMindNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        postedCount++
        // Metadata only: source package and callback count, never content.
        Log.i(TAG, "onNotificationPosted #$postedCount package=${sbn.packageName}")
        scope.launch {
            try {
                handlePosted(sbn)
            } catch (e: Exception) {
                logGenericFailure(e)
            }
        }
    }

    private suspend fun handlePosted(sbn: StatusBarNotification) {
        val input = inputOrNull(sbn) ?: return

        val decision = captureFilter.decide(input)
        if (decision == CaptureDecision.IGNORE) {
            appendCaptureEvent(
                ActivityCategory.CAPTURE_IGNORED,
                "Notification from ${input.sourcePackage} ignored by filter",
                detail = null
            )
            return
        }
        val incoming = NotificationCanonicalizer.toCapture(
            input,
            capturedAt = container.timeProvider.now()
        )
        val existingLatest = container.notificationCaptureRepository.findLatestByIdentity(
            sourcePackage = incoming.sourcePackage,
            notificationKey = incoming.notificationKey
        )
        val relation = CaptureDeduplication.classify(existingLatest, incoming)

        when (val result = container.notificationCaptureRepository.insertIfAbsent(incoming)) {
            is CaptureInsertOutcome.Inserted -> {
                container.captureWorkScheduler.scheduleDrain("notification-insert")
                if (relation == CaptureRelation.NEW_VERSION) {
                    appendCaptureEvent(
                        ActivityCategory.CAPTURE_VERSIONED,
                        "Updated notification captured as a new version",
                        detail = "previous capture #${existingLatest!!.id}"
                    )
                }
            }

            is CaptureInsertOutcome.AlreadyCaptured -> {
                // Stranded CAPTURED rows (e.g. from an earlier build whose drain
                // never ran) must still be rescued when their content redelivers.
                container.captureWorkScheduler.scheduleDrain("notification-duplicate")
                if (relation == CaptureRelation.NEW_VERSION || relation == CaptureRelation.EXACT_DUPLICATE) {
                    appendCaptureEvent(
                        ActivityCategory.CAPTURE_DUPLICATE,
                        "Duplicate delivery of an already captured notification ignored",
                        detail = "existing capture #${result.existing.id}"
                    )
                }
            }
        }
    }

    private suspend fun appendCaptureEvent(
        category: ActivityCategory,
        message: String,
        detail: String?
    ) {
        container.activityLogRepository.append(
            com.notrishabhjain.taskmind.domain.model.ActivityLogEntry(
                category = category,
                message = message,
                detail = detail,
                taskId = null,
                createdAt = container.timeProvider.now()
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Removals are intentionally not persisted in 4B.
    }

    private fun captureOrNull(sbn: StatusBarNotification): com.notrishabhjain.taskmind.domain.model.NotificationCapture? {
        val input = inputOrNull(sbn) ?: return null
        return NotificationCanonicalizer.toCapture(
            input,
            capturedAt = container.timeProvider.now()
        )
    }

    private fun inputOrNull(sbn: StatusBarNotification): RawNotificationInput? {
        val ownPackage = applicationContext.packageName
        val sourcePackage = sbn.packageName ?: return null
        if (NotificationCanonicalizer.isSelfNotification(sourcePackage, ownPackage)) return null

        val notification = sbn.notification ?: return null
        val extras = notification.extras
        fun extra(key: String): String? = extras?.getCharSequence(key)?.toString()

        return RawNotificationInput(
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
        Log.w(TAG, "capture failed: ${e::class.java.simpleName}")
    }

    companion object {
        private const val TAG = "TaskMindListener"
    }
}
