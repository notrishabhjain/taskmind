package com.notrishabhjain.taskmind.ui.captures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.domain.model.NotificationCapture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturedNotificationsScreen(
    viewModel: CapturedNotificationsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCaptureId by rememberSaveable { mutableStateOf<Long?>(null) }

    if (selectedCaptureId != null) {
        CaptureDetailContent(
            viewModel = viewModel,
            captureId = selectedCaptureId!!,
            onBack = { selectedCaptureId = null }
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarHost(title = stringResource(R.string.captures_title), onBack = onBack)
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

        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.captures_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.rows, key = { it.id }) { row ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedCaptureId = row.id }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = row.sourceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (row.title != null) {
                            Text(
                                text = row.title!!,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (row.preview != null && row.preview != row.title) {
                            Text(
                                text = row.preview!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = row.timestampLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(row.stateLabelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureDetailContent(
    viewModel: CapturedNotificationsViewModel,
    captureId: Long,
    onBack: () -> Unit
) {
    val capture by viewModel.observeCapture(captureId)
        .collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBarHost(
                title = stringResource(R.string.captures_detail_title),
                onBack = onBack
            )
        }
    ) { innerPadding ->
        val current = capture
        if (current == null) {
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
            DetailSection(stringResource(R.string.captures_section_identity)) {
                DetailField(stringResource(R.string.captures_field_id), current.id.toString())
                DetailField(stringResource(R.string.captures_field_package), current.sourcePackage)
                DetailField(stringResource(R.string.captures_field_app_label), current.sourceAppLabel)
                DetailField(stringResource(R.string.captures_field_notification_key), current.notificationKey)
                DetailField(stringResource(R.string.captures_field_notification_id), current.notificationId?.toString())
                DetailField(stringResource(R.string.captures_field_tag), current.notificationTag)
                DetailField(stringResource(R.string.captures_field_post_time), formatMillis(current.postTime.toEpochMilli()))
                DetailField(stringResource(R.string.captures_field_created), formatMillis(current.createdAt.toEpochMilli()))
                DetailField(stringResource(R.string.captures_field_updated), formatMillis(current.updatedAt.toEpochMilli()))
            }

            DetailSection(stringResource(R.string.captures_section_content)) {
                DetailField(stringResource(R.string.captures_field_title), current.title)
                ExpandableDetail(stringResource(R.string.captures_field_text), current.text)
                ExpandableDetail(stringResource(R.string.captures_field_big_text), current.bigText)
                DetailField(stringResource(R.string.captures_field_sub_text), current.subText)
                DetailField(stringResource(R.string.captures_field_info_text), current.infoText)
                DetailField(stringResource(R.string.captures_field_conversation_title), current.conversationTitle)
                ExpandableDetail(
                    stringResource(R.string.captures_field_canonical_source_text),
                    current.canonicalSourceText
                )
            }

            DetailSection(stringResource(R.string.captures_section_processing)) {
                DetailField(stringResource(R.string.captures_field_state), current.state.name)
                DetailField(stringResource(R.string.captures_field_retry_count), current.retryCount.toString())
                DetailField(stringResource(R.string.captures_field_last_error), current.lastError)
                DetailField(stringResource(R.string.captures_field_resulting_task_id), current.resultingTaskId?.toString())
                DetailField(stringResource(R.string.captures_field_processed_at), current.processedAt?.toEpochMilli()?.let(::formatMillis))
            }

            DetailSection(stringResource(R.string.captures_section_integrity)) {
                DetailField(stringResource(R.string.captures_field_content_hash), current.contentHash)
                ExpandableDetail(stringResource(R.string.captures_field_idempotency_key), current.idempotencyKey)
                ExpandableDetail(stringResource(R.string.captures_field_source_ref), current.sourceRef)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarHost(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        }
    )
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun DetailField(label: String, value: String?) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = value ?: stringResource(R.string.captures_value_none),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ExpandableDetail(label: String, value: String?) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Text(
        text = (if (expanded) "▼ " else "▶ ") + label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    )
    if (expanded && !value.isNullOrEmpty()) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    } else if (value != null) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMillis(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).toString()
