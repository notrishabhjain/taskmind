package com.notrishabhjain.taskmind.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.TaskSort
import com.notrishabhjain.taskmind.domain.model.TaskView

data class TasksActions(
    val onViewSelected: (TaskView) -> Unit = {},
    val onSortSelected: (TaskSort) -> Unit = {},
    val onSearchChanged: (String) -> Unit = {},
    val onSearchClosed: () -> Unit = {},
    val onToggleComplete: (TaskRowUi) -> Unit = {},
    val onArchiveToggled: (TaskRowUi) -> Unit = {},
    val onEditRequested: (Long) -> Unit = {},
    val onDeleteRequested: (TaskRowUi) -> Unit = {},
    val onDeleteConfirmed: () -> Unit = {},
    val onDeleteDismissed: () -> Unit = {},
    val onAddClicked: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(state: TasksUiState, actions: TasksActions, modifier: Modifier = Modifier) {
    var searchVisible by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = {
                        if (searchVisible) actions.onSearchClosed()
                        searchVisible = !searchVisible
                    }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(
                                if (searchVisible) R.string.cd_close_search else R.string.cd_open_search
                            )
                        )
                    }
                    Box {
                        TextButton(onClick = { sortMenuOpen = true }) {
                            Text(text = stringResource(R.string.sort_prefix, sortLabel(state.sort)))
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            TaskSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(sort)) },
                                    onClick = {
                                        sortMenuOpen = false
                                        actions.onSortSelected(sort)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onAddClicked) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add_task)
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (searchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = actions.onSearchChanged,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            actions.onSearchClosed()
                            searchVisible = false
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(TaskView.entries.toList()) { view ->
                    FilterChip(
                        selected = state.view == view,
                        onClick = { actions.onViewSelected(view) },
                        label = { Text(viewLabel(view)) }
                    )
                }
            }

            if (state.isEmpty) {
                EmptyState(view = state.view, searching = state.searchQuery.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        TaskRow(
                            row = row,
                            onToggleComplete = actions.onToggleComplete,
                            onEdit = actions.onEditRequested,
                            onArchiveToggled = actions.onArchiveToggled,
                            onDeleteRequested = actions.onDeleteRequested
                        )
                    }
                }
            }
        }
    }

    if (state.pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = actions.onDeleteDismissed,
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = actions.onDeleteConfirmed) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDeleteDismissed) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TaskRow(
    row: TaskRowUi,
    onToggleComplete: (TaskRowUi) -> Unit,
    onEdit: (Long) -> Unit,
    onArchiveToggled: (TaskRowUi) -> Unit,
    onDeleteRequested: (TaskRowUi) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val priorityLabel = priorityLabel(row.priority)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Checkbox(
                checked = row.completed,
                onCheckedChange = { onToggleComplete(row) },
                modifier = Modifier.semantics {
                    contentDescription = "$priorityLabel: ${row.title}"
                }
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityDot(priority = row.priority)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        textDecoration = if (row.completed) TextDecoration.LineThrough else null,
                        color = if (row.completed) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (row.dueLabel != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = if (row.overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (row.overdue) {
                                stringResource(R.string.due_overdue_suffix, row.dueLabel!!)
                            } else {
                                row.dueLabel!!
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options, row.title)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_edit)) },
                        onClick = {
                            menuOpen = false
                            onEdit(row.id)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(if (row.archived) R.string.menu_unarchive else R.string.menu_archive)
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onArchiveToggled(row)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.menu_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDeleteRequested(row)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityDot(priority: Priority) {
    val color = when (priority) {
        Priority.URGENT -> MaterialTheme.colorScheme.error
        Priority.HIGH -> MaterialTheme.colorScheme.tertiary
        Priority.MEDIUM -> MaterialTheme.colorScheme.primary
        Priority.LOW -> MaterialTheme.colorScheme.outline
    }
    val label = priorityLabel(priority)
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = label }
    )
}

@Composable
private fun EmptyState(view: TaskView, searching: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        val message = when {
            searching -> stringResource(R.string.empty_search_results)
            view == TaskView.TODAY -> stringResource(R.string.empty_today)
            view == TaskView.UPCOMING -> stringResource(R.string.empty_upcoming)
            view == TaskView.OVERDUE -> stringResource(R.string.empty_overdue)
            view == TaskView.COMPLETED -> stringResource(R.string.empty_completed)
            view == TaskView.ARCHIVED -> stringResource(R.string.empty_archived)
            else -> stringResource(R.string.empty_all)
        }
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun viewLabel(view: TaskView): String = stringResource(
    when (view) {
        TaskView.TODAY -> R.string.view_today
        TaskView.UPCOMING -> R.string.view_upcoming
        TaskView.OVERDUE -> R.string.view_overdue
        TaskView.COMPLETED -> R.string.view_completed
        TaskView.ARCHIVED -> R.string.view_archived
        TaskView.ALL -> R.string.view_all
    }
)

@Composable
private fun sortLabel(sort: TaskSort): String = stringResource(
    when (sort) {
        TaskSort.DUE_DATE -> R.string.sort_due_date
        TaskSort.PRIORITY -> R.string.sort_priority
        TaskSort.CREATED -> R.string.sort_created
    }
)

@Composable
private fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.URGENT -> R.string.priority_urgent
        Priority.HIGH -> R.string.priority_high
        Priority.MEDIUM -> R.string.priority_medium
        Priority.LOW -> R.string.priority_low
    }
)
