package com.notrishabhjain.taskmind.domain.repository

import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry

interface ActivityLogRepository {

    suspend fun append(entry: ActivityLogEntry)

    companion object {
        const val RETENTION_LIMIT = 300
    }
}
