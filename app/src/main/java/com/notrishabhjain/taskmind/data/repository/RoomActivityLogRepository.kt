package com.notrishabhjain.taskmind.data.repository

import com.notrishabhjain.taskmind.data.db.dao.ActivityLogDao
import com.notrishabhjain.taskmind.data.mapper.toDomain
import com.notrishabhjain.taskmind.data.mapper.toEntity
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomActivityLogRepository(
    private val activityLogDao: ActivityLogDao
) : ActivityLogRepository {

    override suspend fun append(entry: ActivityLogEntry) {
        activityLogDao.appendBounded(entry.toEntity(), ActivityLogRepository.RETENTION_LIMIT)
    }

    override fun observeRecent(limit: Int): Flow<List<ActivityLogEntry>> =
        activityLogDao.observeRecent(limit).map { entities -> entities.map { it.toDomain() } }
}
