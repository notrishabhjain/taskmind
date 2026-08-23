package com.notrishabhjain.taskmind.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.notrishabhjain.taskmind.data.db.dao.ActivityLogDao
import com.notrishabhjain.taskmind.data.db.dao.NotificationCaptureDao
import com.notrishabhjain.taskmind.data.db.dao.ProjectTagDao
import com.notrishabhjain.taskmind.data.db.dao.ReviewDao
import com.notrishabhjain.taskmind.data.db.dao.TaskDao
import com.notrishabhjain.taskmind.data.db.entity.ActivityLogEntity
import com.notrishabhjain.taskmind.data.db.entity.NotificationCaptureEntity
import com.notrishabhjain.taskmind.data.db.entity.ProjectEntity
import com.notrishabhjain.taskmind.data.db.entity.ReviewItemEntity
import com.notrishabhjain.taskmind.data.db.entity.TagEntity
import com.notrishabhjain.taskmind.data.db.entity.TaskEntity
import com.notrishabhjain.taskmind.data.db.entity.TaskTagCrossRef

@Database(
    entities = [
        TaskEntity::class,
        ReviewItemEntity::class,
        ActivityLogEntity::class,
        ProjectEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        NotificationCaptureEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TaskMindDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun reviewDao(): ReviewDao

    abstract fun activityLogDao(): ActivityLogDao

    abstract fun projectTagDao(): ProjectTagDao

    abstract fun notificationCaptureDao(): NotificationCaptureDao

    companion object {
        const val NAME = "taskmind.db"
    }
}
