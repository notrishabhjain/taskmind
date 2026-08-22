package com.notrishabhjain.taskmind.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.notrishabhjain.taskmind.data.db.entity.TaskEntity
import com.notrishabhjain.taskmind.data.db.entity.TaskTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TaskEntity?

    @Query(
        "SELECT * FROM tasks WHERE sourceType = :sourceType AND sourceRef = :sourceRef AND titleKey = :titleKey LIMIT 1"
    )
    suspend fun findByLogicalKey(sourceType: String, sourceRef: String, titleKey: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskTags(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun clearTaskTags(taskId: Long)

    @Query("SELECT tagId FROM task_tags WHERE taskId = :taskId")
    suspend fun tagIdsForTask(taskId: Long): List<Long>

    @RawQuery(observedEntities = [TaskEntity::class, TaskTagCrossRef::class])
    fun observeTasks(query: SupportSQLiteQuery): Flow<List<TaskEntity>>
}
