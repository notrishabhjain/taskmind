package com.notrishabhjain.taskmind.data.repository

import com.notrishabhjain.taskmind.data.db.dao.NotificationCaptureDao
import com.notrishabhjain.taskmind.data.mapper.toDomain
import com.notrishabhjain.taskmind.data.mapper.toEntity
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import com.notrishabhjain.taskmind.domain.repository.CaptureInsertOutcome
import com.notrishabhjain.taskmind.domain.repository.NotificationCaptureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNotificationCaptureRepository(
    private val notificationCaptureDao: NotificationCaptureDao
) : NotificationCaptureRepository {

    override suspend fun insertIfAbsent(capture: NotificationCapture): CaptureInsertOutcome {
        val newId = notificationCaptureDao.insert(capture.toEntity())
        if (newId != -1L) {
            return CaptureInsertOutcome.Inserted(requireNotNull(loadById(newId)))
        }
        val existing = notificationCaptureDao.findByIdempotencyKey(capture.idempotencyKey)?.toDomain()
            ?: throw IllegalStateException(
                "Insert reported a duplicate capture but no row exists for key ${capture.idempotencyKey}"
            )
        return CaptureInsertOutcome.AlreadyCaptured(existing)
    }

    override suspend fun findById(id: Long): NotificationCapture? = loadById(id)

    override suspend fun findByIdempotencyKey(idempotencyKey: String): NotificationCapture? =
        notificationCaptureDao.findByIdempotencyKey(idempotencyKey)?.toDomain()

    override suspend fun update(capture: NotificationCapture) {
        notificationCaptureDao.update(capture.toEntity())
    }

    override fun observeByState(state: CaptureState): Flow<List<NotificationCapture>> =
        notificationCaptureDao.observeByState(state.name)
            .map { entities -> entities.map { it.toDomain() } }

    private suspend fun loadById(id: Long): NotificationCapture? =
        notificationCaptureDao.findById(id)?.toDomain()
}
