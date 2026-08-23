package com.notrishabhjain.taskmind.domain.repository

import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import kotlinx.coroutines.flow.Flow

interface ActivityLogRepository {

    suspend fun append(entry: ActivityLogEntry)

    fun observeRecent(limit: Int): Flow<List<ActivityLogEntry>>

    companion object {
        const val RETENTION_LIMIT = 300
        const val RECENT_DISPLAY_LIMIT = 100
    }
}
