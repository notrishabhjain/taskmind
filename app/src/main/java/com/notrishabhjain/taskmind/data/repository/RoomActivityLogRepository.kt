package com.notrishabhjain.taskmind.data.repository

import com.notrishabhjain.taskmind.data.db.dao.ActivityLogDao
import com.notrishabhjain.taskmind.data.mapper.toEntity
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository

class RoomActivityLogRepository(
    private val activityLogDao: ActivityLogDao
) : ActivityLogRepository {

    override suspend fun append(entry: ActivityLogEntry) {
        activityLogDao.appendBounded(entry.toEntity(), ActivityLogRepository.RETENTION_LIMIT)
    }
}
