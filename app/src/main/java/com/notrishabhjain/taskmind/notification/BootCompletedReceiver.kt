package com.notrishabhjain.taskmind.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-enqueues the capture drain/maintenance work after a device reboot so
 * pending notifications resume processing without user interaction.
 *
 * WorkManager persists its own work, but CAPTURED rows that never got a
 * surviving WorkRequest (process death between insert and enqueue) are
 * recovered by this lightweight receiver: it only schedules work.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val container = (context.applicationContext as com.notrishabhjain.taskmind.TaskMindApplication).container
                container.captureWorkScheduler.scheduleDrain()
                container.captureWorkScheduler.scheduleMaintenance()
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
