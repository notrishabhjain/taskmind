package com.notrishabhjain.taskmind.domain.repository

import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ReviewRepository {

    suspend fun insert(item: ReviewItem): ReviewItem

    suspend fun findById(id: Long): ReviewItem?

    fun observePending(): Flow<List<ReviewItem>>

    suspend fun markDecided(id: Long, status: ReviewStatus, resultingTaskId: Long?, decidedAt: Instant)
}
