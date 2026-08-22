package com.notrishabhjain.taskmind.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.notrishabhjain.taskmind.data.db.entity.ProjectEntity
import com.notrishabhjain.taskmind.data.db.entity.TagEntity

@Dao
interface ProjectTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(entity: TagEntity): Long

    @Query("SELECT * FROM tags WHERE nameKey = :nameKey LIMIT 1")
    suspend fun findTag(nameKey: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProject(entity: ProjectEntity): Long

    @Query("SELECT * FROM projects WHERE nameKey = :nameKey LIMIT 1")
    suspend fun findProject(nameKey: String): ProjectEntity?
}
