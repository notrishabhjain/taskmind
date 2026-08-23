package com.notrishabhjain.taskmind.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.domain.model.Priority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskScreen(
    taskId: Long?,
    onClose: () -> Unit,
    viewModel: EditTaskViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = state.saveErrorRes?.let { stringResource(it) }

    LaunchedEffect(saveErrorMessage) {
        if (saveErrorMessage != null) {
            snackbarHostState.showSnackbar(saveErrorMessage)
            viewModel.onErrorMessageShown()
        }
    }

    if (state.savedAndClosed) {
        onClose()
        return
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (taskId == null) R.string.editor_title_create else R.string.editor_title_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text(stringResource(R.string.editor_title_field)) },
                isError = state.titleError,
                supportingText = {
                    if (state.titleError) Text(stringResource(R.string.editor_title_error))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text(stringResource(R.string.editor_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.editor_priority),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { priority ->
                    FilterChip(
                        selected = state.priority == priority,
                        onClick = { viewModel.onPriorityChanged(priority) },
                        label = { Text(stringResource(priorityLabelRes(priority))) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.editor_due),
                style = MaterialTheme.typography.labelLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.dueDate?.format(DATE_FORMATTER)
                            ?: stringResource(R.string.editor_no_due_date),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.dueDate == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (state.dueDate != null && state.dueTime != null) {
                        Text(
                            text = state.dueTime!!.format(TIME_FORMATTER),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Text(stringResource(R.string.editor_pick_date))
                }
                if (state.dueDate != null) {
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(
                            stringResource(
                                if (state.dueTime == null) R.string.editor_add_time
                                else R.string.editor_change_time
                            )
                        )
                    }
                    TextButton(onClick = viewModel::onDueDateCleared) {
                        Text(stringResource(R.string.editor_clear_date))
                    }
                }
            }

            if (taskId == null) {
                OutlinedTextField(
                    value = state.projectName,
                    onValueChange = viewModel::onProjectNameChanged,
                    label = { Text(stringResource(R.string.editor_project)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.tagsInput,
                    onValueChange = viewModel::onTagsChanged,
                    label = { Text(stringResource(R.string.editor_tags)) },
                    supportingText = { Text(stringResource(R.string.editor_tags_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving && !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.editor_save))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dueDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        viewModel.onDateSelected(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.dueTime?.hour ?: DEFAULT_DUE_HOUR,
            initialMinute = state.dueTime?.minute ?: 0,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.editor_pick_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private const val DEFAULT_DUE_HOUR = 9

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun priorityLabelRes(priority: Priority): Int = when (priority) {
    Priority.URGENT -> R.string.priority_urgent
    Priority.HIGH -> R.string.priority_high
    Priority.MEDIUM -> R.string.priority_medium
    Priority.LOW -> R.string.priority_low
}
