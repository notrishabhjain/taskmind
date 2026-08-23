package com.notrishabhjain.taskmind.ui.captures

import com.notrishabhjain.taskmind.domain.intake.FakeNotificationCaptureRepository
import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturedNotificationsViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val repository = FakeNotificationCaptureRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(displayLimit: Int = 10): CapturedNotificationsViewModel =
        CapturedNotificationsViewModel(
            notificationCaptureRepository = repository,
            displayLimit = displayLimit,
            zoneId = ZoneOffset.UTC
        )

    private fun TestScope.collectInBackground(vm: CapturedNotificationsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
    }

    private suspend fun seed(
        idempotencyKey: String,
        title: String?,
        createdAtMillis: Long,
        state: CaptureState = CaptureState.CAPTURED
    ): NotificationCapture {
        val capture = NotificationCapture(
            idempotencyKey = idempotencyKey,
            sourcePackage = "com.whatsapp",
            sourceAppLabel = "WhatsApp",
            notificationKey = "key-$idempotencyKey",
            postTime = Instant.ofEpochMilli(createdAtMillis),
            canonicalSourceText = title ?: "",
            contentHash = "hash-$idempotencyKey",
            sourceRef = "notification:com.whatsapp:key-$idempotencyKey",
            title = title,
            text = title,
            state = state,
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            updatedAt = Instant.ofEpochMilli(createdAtMillis)
        )
        val outcome = repository.insertIfAbsent(capture)
        return (outcome as com.notrishabhjain.taskmind.domain.repository.CaptureInsertOutcome.Inserted).capture
    }

    @Test
    fun `rows are newest first`() = runTest(scheduler) {
        val vm = viewModel()
        collectInBackground(vm)
        seed("k1", "oldest", 1_000L)
        seed("k2", "newest", 3_000L)
        advanceUntilIdle()

        assertEquals(
            listOf("newest", "oldest"),
            vm.uiState.value.rows.map { it.title }
        )
        assertTrue(!vm.uiState.value.loading)
    }

    @Test
    fun `display limit bounds the rows`() = runTest(scheduler) {
        repeat(5) { index -> seed("k$index", "event $index", index * 1_000L) }

        val vm = viewModel(displayLimit = 3)
        collectInBackground(vm)
        advanceUntilIdle()

        assertEquals(
            listOf("event 4", "event 3", "event 2"),
            vm.uiState.value.rows.map { it.title }
        )
    }

    @Test
    fun `state starts loading then reflects seeded rows`() = runTest(scheduler) {
        val vm = viewModel()
        collectInBackground(vm)

        assertTrue(vm.uiState.value.loading)

        seed("k1", "later seeded", 2_000L)
        advanceUntilIdle()

        assertTrue(!vm.uiState.value.loading)
        assertEquals(1, vm.uiState.value.rows.size)
    }

    @Test
    fun `row mapping exposes source label title preview and state`() = runTest(scheduler) {
        val vm = viewModel()
        collectInBackground(vm)
        seed("k9", "Pay electricity bill", 5_000L)
        advanceUntilIdle()

        val row = vm.uiState.value.rows.single()

        assertEquals("WhatsApp", row.sourceLabel)
        assertEquals("Pay electricity bill", row.title)
        assertEquals("Pay electricity bill", row.preview)
        assertEquals(
            com.notrishabhjain.taskmind.R.string.capture_state_captured,
            row.stateLabelRes
        )
    }

    @Test
    fun `detail lookup resolves by id and returns null for unknown ids`() = runTest(scheduler) {
        val seeded = seed("k7", "Call dentist", 4_000L)
        val vm = viewModel()
        advanceUntilIdle()

        val found = vm.observeCapture(seeded.id).first()
        val missing = vm.observeCapture(999L).first()

        assertEquals("Call dentist", found!!.title)
        assertNull(missing)
    }
}
