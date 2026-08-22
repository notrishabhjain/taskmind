package com.notrishabhjain.taskmind.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.notrishabhjain.taskmind.data.db.entity.ActivityLogEntity

@Dao
interface ActivityLogDao {

    @Insert
    suspend fun insert(entity: ActivityLogEntity): Long

    @Query(
        "DELETE FROM activity_log WHERE id NOT IN (" +
            "SELECT id FROM activity_log ORDER BY createdAt DESC, id DESC LIMIT :retentionLimit" +
            ")"
    )
    suspend fun trimTo(retentionLimit: Int)

    @Transaction
    suspend fun appendBounded(entity: ActivityLogEntity, retentionLimit: Int) {
        insert(entity)
        trimTo(retentionLimit)
    }
}
