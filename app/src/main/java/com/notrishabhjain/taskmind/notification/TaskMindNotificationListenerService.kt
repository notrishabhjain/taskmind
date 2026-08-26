package com.notrishabhjain.taskmind.notification

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

    private lateinit var notificationExtractor: AndroidNotificationExtractor

    private var postedCount = 0

    override fun onCreate() {
        super.onCreate()
        container = (applicationContext as com.notrishabhjain.taskmind.TaskMindApplication).container
        captureFilter = CaptureFilter(
            NotificationCapturePolicy(selfPackage = packageName)
        )
        notificationExtractor = AndroidNotificationExtractor(applicationContext)
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

    override fun onListenerDisconnected() {
        container.notificationListenerConnected.value = false
        Log.i(TAG, "onListenerDisconnected -> requesting rebind")
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
        val snapshot = snapshotOrNull(sbn) ?: return

        // CaptureFilter operates on the presentation projection of the snapshot.
        val filterInput = RawNotificationInput(
            sourcePackage = snapshot.packageName,
            notificationKey = snapshot.notificationKey,
            postTimeMillis = snapshot.postTimeMs,
            title = snapshot.title,
            text = snapshot.text,
            bigText = snapshot.bigText,
            subText = snapshot.subText,
            infoText = snapshot.infoText,
            conversationTitle = snapshot.conversation?.title,
            category = snapshot.category,
            appLabel = snapshot.appLabel
        )
        val decision = captureFilter.decide(filterInput)
        if (decision == CaptureDecision.IGNORE) {
            appendCaptureEvent(
                ActivityCategory.CAPTURE_IGNORED,
                "Notification from ${snapshot.packageName} ignored by filter",
                detail = null
            )
            return
        }
        val incoming = NotificationCanonicalizer.toCapture(
            snapshot,
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

    private fun snapshotOrNull(sbn: StatusBarNotification): NotificationSnapshot? {
        val sourcePackage = sbn.packageName ?: return null
        if (NotificationCanonicalizer.isSelfNotification(sourcePackage, applicationContext.packageName)) {
            return null
        }
        if (sbn.notification == null) return null
        return notificationExtractor.extract(sbn)
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
