package com.notrishabhjain.taskmind.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {

    @Test
    fun defaultStateIsEmpty() {
        val state = HomeUiState()

        assertTrue(state.hasNoTasks)
        assertEquals(0, state.taskCount)
    }

    @Test
    fun stateWithTasksIsNotEmpty() {
        val state = HomeUiState(taskCount = 3)

        assertFalse(state.hasNoTasks)
        assertEquals(3, state.taskCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTaskCountIsRejected() {
        HomeUiState(taskCount = -1)
    }
}
