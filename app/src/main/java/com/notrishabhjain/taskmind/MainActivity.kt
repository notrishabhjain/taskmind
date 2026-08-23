package com.notrishabhjain.taskmind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notrishabhjain.taskmind.di.AppContainer
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
    var destination by rememberSaveable { mutableStateOf(Destination.TASKS) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorTaskId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = editorOpen) {
        editorOpen = false
        editorTaskId = null
    }

    val tasksViewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tasksViewModel.onHostResumed()
                Lifecycle.Event.ON_PAUSE -> tasksViewModel.onHostPaused()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                Destination.TASKS -> TasksScreen(
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
                    modifier = Modifier
                )

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
