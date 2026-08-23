package com.notrishabhjain.taskmind.data.repository

import com.notrishabhjain.taskmind.data.db.dao.ReviewDao
import com.notrishabhjain.taskmind.data.mapper.toDomain
import com.notrishabhjain.taskmind.data.mapper.toEntity
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.repository.ReviewRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReviewRepository(
    private val reviewDao: ReviewDao
) : ReviewRepository {

    override suspend fun insert(item: ReviewItem): ReviewItem {
        val newId = reviewDao.insert(item.toEntity())
        return item.copy(id = newId)
    }

    override suspend fun findById(id: Long): ReviewItem? =
        reviewDao.findById(id)?.toDomain()

    override fun observePending(): Flow<List<ReviewItem>> =
        reviewDao.observePending().map { entities -> entities.map { it.toDomain() } }

    override suspend fun markDecided(
        id: Long,
        status: ReviewStatus,
        resultingTaskId: Long?,
        decidedAt: Instant
    ) {
        reviewDao.markDecided(id, status.name, resultingTaskId, decidedAt.toEpochMilli())
    }
}
