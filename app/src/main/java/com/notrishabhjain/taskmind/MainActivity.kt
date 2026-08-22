package com.notrishabhjain.taskmind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notrishabhjain.taskmind.di.AppContainer
import com.notrishabhjain.taskmind.ui.editor.EditTaskScreen
import com.notrishabhjain.taskmind.ui.editor.EditTaskViewModel
import com.notrishabhjain.taskmind.ui.tasks.TasksActions
import com.notrishabhjain.taskmind.ui.tasks.TasksScreen
import com.notrishabhjain.taskmind.ui.tasks.TasksViewModel
import com.notrishabhjain.taskmind.ui.theme.TaskMindTheme

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
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorTaskId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = editorOpen) {
        editorOpen = false
        editorTaskId = null
    }

    if (editorOpen) {
        val viewModel: EditTaskViewModel = viewModel(
            key = "edit-task-$editorTaskId",
            factory = EditTaskViewModel.factory(editorTaskId)
        )
        EditTaskScreen(
            taskId = editorTaskId,
            onClose = {
                editorOpen = false
                editorTaskId = null
            },
            viewModel = viewModel
        )
    } else {
        val tasksViewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory)

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) tasksViewModel.onHostResumed()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

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
            )
        )
    }
}

@Composable
private fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember { (context.applicationContext as TaskMindApplication).container }
}
