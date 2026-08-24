package com.notrishabhjain.taskmind.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.notrishabhjain.taskmind.data.db.entity.NotificationCaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationCaptureDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationCaptureEntity): Long

    @Query("SELECT * FROM notification_captures WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): NotificationCaptureEntity?

    @Query("SELECT * FROM notification_captures WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun findByIdempotencyKey(idempotencyKey: String): NotificationCaptureEntity?

    @Update
    suspend fun update(entity: NotificationCaptureEntity)

    @Query(
        "SELECT * FROM notification_captures WHERE state = :state ORDER BY createdAt ASC, id ASC"
    )
    fun observeByState(state: String): Flow<List<NotificationCaptureEntity>>

    @Query(
        "SELECT * FROM notification_captures " +
            "WHERE state IN ('CAPTURED', 'QUEUED', 'RETRY_PENDING', 'DEFERRED') " +
            "ORDER BY createdAt ASC, id ASC LIMIT :limit"
    )
    suspend fun selectDueForProcessing(limit: Int): List<NotificationCaptureEntity>

    @Query(
        "UPDATE notification_captures " +
            "SET state = 'PROCESSING', updatedAt = :now " +
            "WHERE id = :id AND state IN ('CAPTURED', 'QUEUED', 'RETRY_PENDING', 'DEFERRED')"
    )
    suspend fun claimForProcessing(id: Long, now: Long): Int

    @Query(
        "UPDATE notification_captures SET state = 'QUEUED', updatedAt = :now " +
            "WHERE state = 'CAPTURED'"
    )
    suspend fun promoteCapturedToQueued(now: Long): Int

    @Query(
        "UPDATE notification_captures SET state = 'QUEUED', updatedAt = :now " +
            "WHERE state = 'PROCESSING' AND updatedAt < :staleCutoff"
    )
    suspend fun recoverStaleProcessing(staleCutoff: Long, now: Long): Int

    @Query(
        "SELECT * FROM notification_captures ORDER BY createdAt DESC, id DESC LIMIT :limit"
    )
    fun observeRecentCaptures(limit: Int): Flow<List<NotificationCaptureEntity>>

    @Query("SELECT * FROM notification_captures WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<NotificationCaptureEntity?>

    @Query(
        "SELECT * FROM notification_captures " +
            "WHERE sourcePackage = :sourcePackage AND notificationKey = :notificationKey " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1"
    )
    suspend fun findLatestByIdentity(sourcePackage: String, notificationKey: String): NotificationCaptureEntity?
}
