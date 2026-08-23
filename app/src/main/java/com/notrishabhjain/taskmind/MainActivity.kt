package com.notrishabhjain.taskmind

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notrishabhjain.taskmind.di.AppContainer
import com.notrishabhjain.taskmind.notification.TaskMindNotificationListenerService
import com.notrishabhjain.taskmind.ui.activitylog.ActivityLogScreen
import com.notrishabhjain.taskmind.ui.activitylog.ActivityLogViewModel
import com.notrishabhjain.taskmind.ui.editor.EditTaskScreen
import com.notrishabhjain.taskmind.ui.editor.EditTaskViewModel
import com.notrishabhjain.taskmind.ui.review.ReviewInboxScreen
import com.notrishabhjain.taskmind.ui.review.ReviewInboxViewModel
import com.notrishabhjain.taskmind.ui.tasks.TasksActions
import com.notrishabhjain.taskmind.ui.tasks.TasksScreen
import com.notrishabhjain.taskmind.ui.tasks.TasksViewModel
import com.notrishabhjain.taskmind.ui.theme.TaskMindTheme

private enum class Destination { TASKS, REVIEW, ACTIVITY }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskMindTheme {
                TaskMindRoot()
            }
        }
    }
}

@Composable
private fun TaskMindRoot(container: AppContainer = rememberAppContainer()) {
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(Destination.TASKS) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var notificationAccessEnabled by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = editorOpen) {
        editorOpen = false
        editorTaskId = null
    }

    val tasksViewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    tasksViewModel.onHostResumed()
                    notificationAccessEnabled = isNotificationAccessGranted(context)
                }

                Lifecycle.Event.ON_PAUSE -> tasksViewModel.onHostPaused()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        notificationAccessEnabled = isNotificationAccessGranted(context)
    }

    if (editorOpen) {
        val editViewModel: EditTaskViewModel = viewModel(
            key = "edit-task-$editorTaskId",
            factory = EditTaskViewModel.factory(editorTaskId)
        )
        EditTaskScreen(
            taskId = editorTaskId,
            onClose = {
                editorOpen = false
                editorTaskId = null
            },
            viewModel = editViewModel
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == Destination.TASKS,
                    onClick = { destination = Destination.TASKS },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_tasks)) }
                )
                NavigationBarItem(
                    selected = destination == Destination.REVIEW,
                    onClick = { destination = Destination.REVIEW },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_review)) }
                )
                NavigationBarItem(
                    selected = destination == Destination.ACTIVITY,
                    onClick = { destination = Destination.ACTIVITY },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_activity)) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (destination) {
                Destination.TASKS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        NotificationAccessBanner(
                            accessEnabled = notificationAccessEnabled,
                            listenerConnected = container.notificationListenerConnected
                                .collectAsStateWithLifecycle().value,
                            onEnableClicked = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            }
                        )
                        TasksScreen(
                            state = tasksViewModel.uiState.collectAsStateWithLifecycle().value,
                            actions = TasksActions(
                                onViewSelected = tasksViewModel::onViewSelected,
                                onSortSelected = tasksViewModel::onSortSelected,
                                onSearchChanged = tasksViewModel::onSearchChanged,
                                onSearchClosed = tasksViewModel::onSearchClosed,
                                onToggleComplete = tasksViewModel::onToggleComplete,
                                onArchiveToggled = tasksViewModel::onArchiveToggled,
                                onEditRequested = { id ->
                                    editorTaskId = id
                                    editorOpen = true
                                },
                                onDeleteRequested = tasksViewModel::onDeleteRequested,
                                onDeleteConfirmed = tasksViewModel::onDeleteConfirmed,
                                onDeleteDismissed = tasksViewModel::onDeleteDismissed,
                                onAddClicked = {
                                    editorTaskId = null
                                    editorOpen = true
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Destination.REVIEW -> {
                    val reviewViewModel: ReviewInboxViewModel =
                        viewModel(factory = ReviewInboxViewModel.Factory)
                    ReviewInboxScreen(viewModel = reviewViewModel)
                }

                Destination.ACTIVITY -> {
                    val activityViewModel: ActivityLogViewModel =
                        viewModel(factory = ActivityLogViewModel.Factory)
                    ActivityLogScreen(viewModel = activityViewModel)
                }
            }
        }
    }
}

@Composable
private fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember { (context.applicationContext as TaskMindApplication).container }
}

private fun isNotificationAccessGranted(context: Context): Boolean {
    val component = ComponentName(context, TaskMindNotificationListenerService::class.java)
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    return notificationManager?.isNotificationListenerAccessGranted(component) ?: false
}

@Composable
private fun NotificationAccessBanner(
    accessEnabled: Boolean,
    listenerConnected: Boolean,
    onEnableClicked: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.notification_access_label) + ": " + stringResource(
                    if (accessEnabled) R.string.notification_access_enabled
                    else R.string.notification_access_disabled
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    if (listenerConnected) R.string.notification_listener_connected
                    else R.string.notification_listener_disconnected
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (listenerConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!accessEnabled) {
            TextButton(onClick = onEnableClicked) {
                Text(stringResource(R.string.notification_access_enable_action))
            }
        }
    }
}
