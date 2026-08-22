package com.notrishabhjain.taskmind.di

import android.content.Context
import androidx.room.Room
import com.notrishabhjain.taskmind.data.db.TaskMindDatabase
import com.notrishabhjain.taskmind.data.repository.RoomActivityLogRepository
import com.notrishabhjain.taskmind.data.repository.RoomProjectTagRepository
import com.notrishabhjain.taskmind.data.repository.RoomReviewRepository
import com.notrishabhjain.taskmind.data.repository.RoomTaskRepository
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.repository.ProjectTagRepository
import com.notrishabhjain.taskmind.domain.repository.ReviewRepository
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import com.notrishabhjain.taskmind.domain.service.TaskService
import com.notrishabhjain.taskmind.domain.time.SystemTimeProvider
import com.notrishabhjain.taskmind.domain.time.TimeProvider

class AppContainer(context: Context) {

    private val database: TaskMindDatabase = Room.databaseBuilder(
        context.applicationContext,
        TaskMindDatabase::class.java,
        TaskMindDatabase.NAME
    ).build()

    val timeProvider: TimeProvider = SystemTimeProvider()

    val taskRepository: TaskRepository = RoomTaskRepository(database.taskDao())

    val reviewRepository: ReviewRepository = RoomReviewRepository(database.reviewDao())

    val activityLogRepository: ActivityLogRepository =
        RoomActivityLogRepository(database.activityLogDao())

    val projectTagRepository: ProjectTagRepository =
        RoomProjectTagRepository(database.projectTagDao(), timeProvider)

    val taskIntakeService: TaskIntakeService = TaskIntakeService(
        taskRepository = taskRepository,
        reviewRepository = reviewRepository,
        activityLogRepository = activityLogRepository,
        projectTagRepository = projectTagRepository,
        timeProvider = timeProvider
    )

    val taskService: TaskService = TaskService(
        taskRepository = taskRepository,
        projectTagRepository = projectTagRepository,
        activityLogRepository = activityLogRepository,
        timeProvider = timeProvider
    )
}
