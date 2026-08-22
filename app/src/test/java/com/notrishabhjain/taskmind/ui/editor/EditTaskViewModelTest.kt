package com.notrishabhjain.taskmind.ui.editor

import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.intake.FakeProjectTagRepository
import com.notrishabhjain.taskmind.domain.intake.FakeReviewRepository
import com.notrishabhjain.taskmind.domain.intake.FakeTaskRepository
import com.notrishabhjain.taskmind.domain.intake.FixedTimeProvider
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.model.TaskQuery
import com.notrishabhjain.taskmind.domain.repository.DuplicateTaskException
import com.notrishabhjain.taskmind.domain.repository.TaskRepository
import com.notrishabhjain.taskmind.domain.service.TaskService
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditTaskViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val time = FixedTimeProvider(start = 1_000L)
    private val tasks = FakeTaskRepository()
    private val reviews = FakeReviewRepository()
    private val logs = FakeActivityLogRepository()
    private val tags = FakeProjectTagRepository()

    private class AlwaysCollidingRepository(
        private val backing: FakeTaskRepository
    ) : TaskRepository {

        override suspend fun insert(task: Task): Task {
            throw DuplicateTaskException(
                task.sourceType,
                requireNotNull(task.sourceRef) { "manual inserts cannot collide" },
                task.titleKey
            )
        }

        override suspend fun update(task: Task) = backing.update(task)

        override suspend fun findById(id: Long): Task? = backing.findById(id)

        override suspend fun findByLogicalKey(
            sourceType: SourceType,
            sourceRef: String,
            titleKey: String
        ): Task? = backing.findByLogicalKey(sourceType, sourceRef, titleKey)

        override fun observeTasks(
            query: TaskQuery,
            dayStart: Instant,
            dayEnd: Instant
        ): Flow<List<Task>> = backing.observeTasks(query, dayStart, dayEnd)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(taskRepository: TaskRepository, taskId: Long? = null): EditTaskViewModel =
        EditTaskViewModel(
            editingTaskId = taskId,
            taskRepository = taskRepository,
            taskService = TaskService(taskRepository, tags, logs, time),
            intakeService = TaskIntakeService(taskRepository, reviews, logs, tags, time),
            projectTagRepository = tags
        )

    private fun fillForm(vm: EditTaskViewModel) {
        vm.onTitleChanged("Buy milk")
        vm.onNotesChanged("2 liters")
    }

    @Test
    fun `intake processing failure surfaces visible error without discarding input`() = runTest(scheduler) {
        val vm = viewModel(AlwaysCollidingRepository(tasks))
        fillForm(vm)

        vm.save()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.saveErrorRes)
        assertEquals(R.string.editor_save_failed, state.saveErrorRes)
        assertFalse(state.savedAndClosed)
        assertFalse(state.saving)
        assertEquals("Buy milk", state.title)
        assertEquals("2 liters", state.notes)
        assertEquals(0, tasks.size)
        assertEquals(1, logs.countOf(com.notrishabhjain.taskmind.domain.model.ActivityCategory.PROCESSING_FAILED))
    }

    @Test
    fun `error clears once it has been shown`() = runTest(scheduler) {
        val vm = viewModel(AlwaysCollidingRepository(tasks))
        fillForm(vm)
        vm.save()
        advanceUntilIdle()

        vm.onErrorMessageShown()

        assertNull(vm.uiState.value.saveErrorRes)
    }

    @Test
    fun `successful creation closes without error`() = runTest(scheduler) {
        val vm = viewModel(tasks)
        fillForm(vm)

        vm.save()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.savedAndClosed)
        assertNull(state.saveErrorRes)
        assertEquals(1, tasks.size)
    }
}
