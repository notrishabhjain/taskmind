package com.notrishabhjain.taskmind.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.notrishabhjain.taskmind.data.db.entity.ReviewItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Insert
    suspend fun insert(entity: ReviewItemEntity): Long

    @Query("SELECT * FROM review_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReviewItemEntity?

    @Query("SELECT * FROM review_items WHERE status = 'PENDING' ORDER BY createdAt DESC, id DESC")
    fun observePending(): Flow<List<ReviewItemEntity>>

    @Query(
        "UPDATE review_items SET status = :status, resultingTaskId = :resultingTaskId, decidedAt = :decidedAt " +
            "WHERE id = :id"
    )
    suspend fun markDecided(id: Long, status: String, resultingTaskId: Long?, decidedAt: Long)
}
