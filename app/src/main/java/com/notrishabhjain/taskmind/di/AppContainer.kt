package com.notrishabhjain.taskmind.di

import android.content.Context
import androidx.room.Room
import com.notrishabhjain.taskmind.data.db.MIGRATION_1_2
import com.notrishabhjain.taskmind.data.db.TaskMindDatabase
import com.notrishabhjain.taskmind.data.repository.RoomActivityLogRepository
import com.notrishabhjain.taskmind.data.repository.RoomNotificationCaptureRepository
import com.notrishabhjain.taskmind.data.repository.RoomProjectTagRepository
import com.notrishabhjain.taskmind.data.repository.RoomReviewRepository
import com.notrishabhjain.taskmind.data.repository.RoomTaskRepository
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.repository.ActivityLogRepository
import com.notrishabhjain.taskmind.domain.repository.NotificationCaptureRepository
import com.notrishabhjain.taskmind.domain.repository.ProjectTagRepository
import com.notrishabhjain.taskmind.domain.repository.ReviewRepository
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import com.notrishabhjain.taskmind.domain.service.ReviewService
import com.notrishabhjain.taskmind.domain.service.TaskService
import com.notrishabhjain.taskmind.domain.time.SystemTimeProvider
import com.notrishabhjain.taskmind.domain.time.TimeProvider

import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {

    private val database: TaskMindDatabase = Room.databaseBuilder(
        context.applicationContext,
        TaskMindDatabase::class.java,
        TaskMindDatabase.NAME
    )
        .addMigrations(MIGRATION_1_2)
        .build()

    val timeProvider: TimeProvider = SystemTimeProvider()

    val taskRepository: TaskRepository = RoomTaskRepository(database.taskDao())

    val reviewRepository: ReviewRepository = RoomReviewRepository(database.reviewDao())

    val activityLogRepository: ActivityLogRepository =
        RoomActivityLogRepository(database.activityLogDao())

    val projectTagRepository: ProjectTagRepository =
        RoomProjectTagRepository(database.projectTagDao(), timeProvider)

    val notificationCaptureRepository: NotificationCaptureRepository =
        RoomNotificationCaptureRepository(database.notificationCaptureDao())

    val notificationCaptureProcessor: com.notrishabhjain.taskmind.domain.service.NotificationCaptureProcessor =
        com.notrishabhjain.taskmind.data.processor.DeferredNotificationCaptureProcessor()

    val captureProcessingCoordinator: com.notrishabhjain.taskmind.domain.service.CaptureProcessingCoordinator =
        com.notrishabhjain.taskmind.domain.service.CaptureProcessingCoordinator(
            captures = notificationCaptureRepository,
            processor = notificationCaptureProcessor,
            activityLogRepository = activityLogRepository,
            timeProvider = timeProvider
        )

    val captureWorkScheduler: com.notrishabhjain.taskmind.notification.CaptureWorkScheduler =
        com.notrishabhjain.taskmind.notification.CaptureWorkScheduler(
            androidx.work.WorkManager.getInstance(context)
        )

    val captureProcessingWorkerFactory: androidx.work.WorkerFactory =
        com.notrishabhjain.taskmind.data.worker.CaptureProcessingWorker.factory {
            captureProcessingCoordinator
        }

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

    val reviewService: ReviewService = ReviewService(
        reviewRepository = reviewRepository,
        activityLogRepository = activityLogRepository,
        timeProvider = timeProvider,
        taskIntakeService = taskIntakeService
    )

    val notificationListenerConnected = MutableStateFlow(false)
}
