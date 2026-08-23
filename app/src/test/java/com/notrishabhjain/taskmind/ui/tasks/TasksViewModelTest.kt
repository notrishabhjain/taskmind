package com.notrishabhjain.taskmind.ui.tasks

import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.intake.FakeProjectTagRepository
import com.notrishabhjain.taskmind.domain.intake.FakeTaskRepository
import com.notrishabhjain.taskmind.domain.intake.FixedTimeProvider
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.service.TaskService
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val zone: ZoneOffset = ZoneOffset.UTC

    private val day1Noon = LocalDate.of(2026, 8, 22).atTime(12, 0).atZone(zone).toInstant()
    private val day1Start = LocalDate.of(2026, 8, 22).atStartOfDay(zone).toInstant()
    private val day2Start = LocalDate.of(2026, 8, 23).atStartOfDay(zone).toInstant()

    private val time = FixedTimeProvider(start = day1Noon.toEpochMilli())
    private val tasks = FakeTaskRepository()
    private val activityLog = FakeActivityLogRepository()
    private val tags = FakeProjectTagRepository()

    private companion object {
        const val TEST_NEVER_INTERVAL_MS = 1_000_000_000_000L
        const val TEST_TICK_INTERVAL_MS = 60_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(scheduler)
        runBlocking {
            tasks.insert(
                Task(
                    id = 0L,
                    title = "Due today",
                    titleKey = "due today",
                    priority = Priority.HIGH,
                    dueAt = day1Noon.plusSeconds(3_600),
                    sourceType = SourceType.MANUAL,
                    createdAt = day1Start,
                    updatedAt = day1Start
                )
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(resyncIntervalMs: Long = TEST_NEVER_INTERVAL_MS): TasksViewModel =
        TasksViewModel(
            taskRepository = tasks,
            taskService = TaskService(tasks, tags, activityLog, time),
            timeProvider = time,
            zoneId = zone,
            resyncIntervalMs = resyncIntervalMs
        )

    private fun TestScope.collectInBackground(vm: TasksViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
    }

    @Test
    fun `host resume recomputes the today window`() = runTest(scheduler) {
        val vm = viewModel()
        collectInBackground(vm)
        advanceUntilIdle()

        assertEquals(day1Start.toEpochMilli(), tasks.observations.last().dayStartMillis)

        time.advanceBy(24 * 60 * 60 * 1_000L)
        vm.onHostResumed()
        runCurrent()

        assertEquals(day2Start.toEpochMilli(), tasks.observations.last().dayStartMillis)

        vm.onHostPaused()
    }

    @Test
    fun `periodic refresh keeps flowing while host is active`() = runTest(scheduler) {
        val vm = viewModel(resyncIntervalMs = TEST_TICK_INTERVAL_MS)
        collectInBackground(vm)
        advanceUntilIdle()

        val observationsBefore = tasks.observations.size

        vm.onHostResumed()
        runCurrent()
        advanceTimeBy(TEST_TICK_INTERVAL_MS)
        runCurrent()

        assertTrue(tasks.observations.size > observationsBefore)
        assertEquals(day1Start.toEpochMilli(), tasks.observations.last().dayStartMillis)

        vm.onHostPaused()
        advanceUntilIdle()
    }

    @Test
    fun `ui state reflects repository rows`() = runTest(scheduler) {
        val vm = viewModel()
        collectInBackground(vm)
        advanceUntilIdle()

        val state = vm.uiState.value

        assertTrue(!state.loading)
        assertEquals(1, state.rows.size)
        assertEquals("Due today", state.rows.first().title)
        assertTrue(state.rows.first().overdue.not())
    }
}
