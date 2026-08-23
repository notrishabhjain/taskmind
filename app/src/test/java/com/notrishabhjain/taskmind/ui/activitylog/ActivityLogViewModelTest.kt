package com.notrishabhjain.taskmind.ui.activitylog

import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.model.ActivityCategory
import com.notrishabhjain.taskmind.domain.model.ActivityLogEntry
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityLogViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val logs = FakeActivityLogRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(limit: Int): ActivityLogViewModel = ActivityLogViewModel(
        activityLogRepository = logs,
        displayLimit = limit,
        zoneId = ZoneOffset.UTC
    )

    private fun entry(id: Long, atMillis: Long, message: String): ActivityLogEntry =
        ActivityLogEntry(
            id = id,
            category = ActivityCategory.TASK_CREATED,
            message = message,
            detail = null,
            taskId = null,
            createdAt = Instant.ofEpochMilli(atMillis)
        )

    @Test
    fun `entries are newest first`() = runTest(scheduler) {
        logs.append(entry(1, 1_000L, "oldest"))
        logs.append(entry(2, 3_000L, "newest"))
        logs.append(entry(3, 2_000L, "middle"))

        val vm = viewModel(limit = 10)
        advanceUntilIdle()

        assertEquals(
            listOf("newest", "middle", "oldest"),
            vm.uiState.value.rows.map { it.message }
        )
        assertTrue(!vm.uiState.value.loading)
    }

    @Test
    fun `display limit keeps only newest entries`() = runTest(scheduler) {
        repeat(5) { index -> logs.append(entry(index.toLong(), index * 1_000L, "event $index")) }

        val vm = viewModel(limit = 3)
        advanceUntilIdle()

        assertEquals(
            listOf("event 4", "event 3", "event 2"),
            vm.uiState.value.rows.map { it.message }
        )
    }

    @Test
    fun `category and task references are mapped for display`() = runTest(scheduler) {
        logs.append(
            ActivityLogEntry(
                id = 7L,
                category = ActivityCategory.INTAKE_ACCEPTED,
                message = "Confirmed \"Pay bill\"",
                detail = "Review #1 accepted",
                taskId = 5L,
                createdAt = Instant.ofEpochMilli(9_999L)
            )
        )

        val vm = viewModel(limit = 10)
        advanceUntilIdle()

        val row = vm.uiState.value.rows.single()
        assertEquals(
            com.notrishabhjain.taskmind.R.string.category_intake_accepted,
            row.categoryLabelRes
        )
        assertEquals(5L, row.taskId)
        assertTrue(row.detail!!.contains("accepted"))
    }
}
