package com.notrishabhjain.taskmind.domain.repository

import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import kotlinx.coroutines.flow.Flow

sealed interface CaptureInsertOutcome {
    data class Inserted(val capture: NotificationCapture) : CaptureInsertOutcome

    data class AlreadyCaptured(val existing: NotificationCapture) : CaptureInsertOutcome
}

interface NotificationCaptureRepository {

    suspend fun insertIfAbsent(capture: NotificationCapture): CaptureInsertOutcome

    suspend fun findById(id: Long): NotificationCapture?

    suspend fun findByIdempotencyKey(idempotencyKey: String): NotificationCapture?

    suspend fun update(capture: NotificationCapture)

    fun observeByState(state: CaptureState): Flow<List<NotificationCapture>>
}
