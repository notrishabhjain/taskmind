package com.notrishabhjain.taskmind.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.notrishabhjain.taskmind.R
import com.notrishabhjain.taskmind.TaskMindApplication
import com.notrishabhjain.taskmind.di.AppContainer
import com.notrishabhjain.taskmind.domain.intake.FakeActivityLogRepository
import com.notrishabhjain.taskmind.domain.intake.FakeProjectTagRepository
import com.notrishabhjain.taskmind.domain.intake.FakeReviewRepository
import com.notrishabhjain.taskmind.domain.intake.FakeTaskRepository
import com.notrishabhjain.taskmind.domain.intake.FixedTimeProvider
import com.notrishabhjain.taskmind.domain.intake.TaskIntakeService
import com.notrishabhjain.taskmind.domain.model.Priority
import com.notrishabhjain.taskmind.domain.model.ReviewItem
import com.notrishabhjain.taskmind.domain.model.ReviewStatus
import com.notrishabhjain.taskmind.domain.model.SourceType
import com.notrishabhjain.taskmind.domain.model.Task
import com.notrishabhjain.taskmind.domain.service.ReviewService
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class ReviewInboxViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val time = FixedTimeProvider(start = 1_000L)
    private val tasks = FakeTaskRepository()
    private val reviews = FakeReviewRepository()
    private val logs = FakeActivityLogRepository()
    private val tags = FakeProjectTagRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): ReviewInboxViewModel = ReviewInboxViewModel(
        reviewRepository = reviews,
        reviewService = ReviewService(reviews, logs, time, TaskIntakeService(tasks, reviews, logs, tags, time)),
        timeProvider = time
    )

    private fun TestScope.collectInBackground(vm: ReviewInboxViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
    }

    private fun pendingReview(sourceRef: String, title: String): ReviewItem = ReviewItem(
        displayTitle = title,
        titleKey = title.lowercase(),
        priority = Priority.HIGH,
        confidence = 0.55,
        sourceType = SourceType.NOTIFICATION,
        sourceRef = sourceRef,
        sourceText = "please $title today",
        evidence = title.lowercase(),
        createdAt = Instant.ofEpochMilli(500L)
    )

    @Test
    fun `only pending items are exposed newest first`() = runTest(scheduler) {
        reviews.insert(pendingReview("wa:1", "First suggestion"))
        val accepted = reviews.insert(pendingReview("wa:2", "Second suggestion"))
        reviews.markDecided(accepted.id, ReviewStatus.ACCEPTED, 42L, Instant.ofEpochMilli(600L))
        reviews.insert(pendingReview("wa:3", "Third suggestion"))

        val vm = viewModel()
        collectInBackground(vm)
        advanceUntilIdle()

        assertEquals(
            listOf("Third suggestion", "First suggestion"),
            vm.uiState.value.rows.map { it.title }
        )
        assertTrue(vm.uiState.value.rows.none { it.title == "Second suggestion" })
    }

    @Test
    fun `accept removes item from pending inbox`() = runTest(scheduler) {
        reviews.insert(pendingReview("wa:9", "Pay electricity bill"))

        val vm = viewModel()
        collectInBackground(vm)
        advanceUntilIdle()

        vm.onAcceptClicked(vm.uiState.value.rows.single())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertNull(vm.uiState.value.busyItemId)
        assertEquals(1, tasks.size)
        assertEquals(ReviewStatus.ACCEPTED, reviews.findById(1L)!!.status)
    }

    @Test
    fun `dismiss removes item from pending inbox`() = runTest(scheduler) {
        reviews.insert(pendingReview("wa:10", "Water plants"))

        val vm = viewModel()
        collectInBackground(vm)
        advanceUntilIdle()

        vm.onDismissClicked(vm.uiState.value.rows.single())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertEquals(0, tasks.size)
        assertEquals(
            1,
            logs.countOf(com.notrishabhjain.taskmind.domain.model.ActivityCategory.INTAKE_REJECTED)
        )
    }

    @Test
    fun `accepting a logical duplicate surfaces a visible message`() = runTest(scheduler) {
        tasks.insert(
            Task(
                id = 0L,
                title = "Existing task",
                titleKey = "existing task",
                sourceType = SourceType.NOTIFICATION,
                sourceRef = "wa:dup",
                createdAt = Instant.ofEpochMilli(1L),
                updatedAt = Instant.ofEpochMilli(1L)
            )
        )
        reviews.insert(pendingReview("wa:dup", "existing TASK"))

        val vm = viewModel()
        collectInBackground(vm)
        advanceUntilIdle()

        vm.onAcceptClicked(vm.uiState.value.rows.single())
        advanceUntilIdle()

        assertEquals(R.string.review_duplicate_message, vm.uiState.value.messageRes)
        assertEquals(1, tasks.size)
    }
}
