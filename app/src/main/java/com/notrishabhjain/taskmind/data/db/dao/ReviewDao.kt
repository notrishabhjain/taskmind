package com.notrishabhjain.taskmind.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.notrishabhjain.taskmind.data.db.entity.ReviewItemEntity

@Dao
interface ReviewDao {

    @Insert
    suspend fun insert(entity: ReviewItemEntity): Long

    @Query("SELECT * FROM review_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReviewItemEntity?

    @Query(
        "UPDATE review_items SET status = :status, resultingTaskId = :resultingTaskId, decidedAt = :decidedAt " +
            "WHERE id = :id"
    )
    suspend fun markDecided(id: Long, status: String, resultingTaskId: Long?, decidedAt: Long)
}
