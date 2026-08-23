package com.notrishabhjain.taskmind.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCaptureStateTest {

    @Test
    fun `legal transitions are accepted`() {
        val legal = listOf(
            CaptureState.CAPTURED to CaptureState.QUEUED,
            CaptureState.CAPTURED to CaptureState.IGNORED,
            CaptureState.QUEUED to CaptureState.PROCESSING,
            CaptureState.PROCESSING to CaptureState.PROCESSED,
            CaptureState.PROCESSING to CaptureState.REVIEWED,
            CaptureState.PROCESSING to CaptureState.REJECTED,
            CaptureState.PROCESSING to CaptureState.RETRY_PENDING,
            CaptureState.PROCESSING to CaptureState.FAILED,
            CaptureState.RETRY_PENDING to CaptureState.PROCESSING
        )

        legal.forEach { (from, to) ->
            assertTrue("expected $from -> $to legal", NotificationCaptureStateMachine.canTransition(from, to))
        }
    }

    @Test
    fun `illegal transitions are rejected`() {
        val illegal = listOf(
            CaptureState.CAPTURED to CaptureState.CAPTURED,
            CaptureState.CAPTURED to CaptureState.PROCESSED,
            CaptureState.CAPTURED to CaptureState.FAILED,
            CaptureState.CAPTURED to CaptureState.RETRY_PENDING,
            CaptureState.QUEUED to CaptureState.IGNORED,
            CaptureState.QUEUED to CaptureState.PROCESSED,
            CaptureState.PROCESSING to CaptureState.QUEUED,
            CaptureState.PROCESSING to CaptureState.CAPTURED,
            CaptureState.RETRY_PENDING to CaptureState.QUEUED,
            CaptureState.PROCESSED to CaptureState.PROCESSING,
            CaptureState.REVIEWED to CaptureState.FAILED,
            CaptureState.REJECTED to CaptureState.RETRY_PENDING,
            CaptureState.IGNORED to CaptureState.QUEUED,
            CaptureState.FAILED to CaptureState.RETRY_PENDING
        )

        illegal.forEach { (from, to) ->
            assertFalse("expected $from -> $to illegal", NotificationCaptureStateMachine.canTransition(from, to))
        }
    }

    @Test
    fun `terminal states never transition anywhere`() {
        NotificationCaptureStateMachine.TERMINAL_STATES.forEach { terminal ->
            CaptureState.entries.forEach { target ->
                assertFalse(
                    "terminal $terminal must not transition to $target",
                    NotificationCaptureStateMachine.canTransition(terminal, target)
                )
            }
        }

        assertEquals(
            setOf(CaptureState.PROCESSED, CaptureState.REVIEWED, CaptureState.REJECTED, CaptureState.IGNORED, CaptureState.FAILED),
            NotificationCaptureStateMachine.TERMINAL_STATES
        )
    }

    @Test
    fun `requireValidTransition throws for illegal moves`() {
        var thrown: IllegalStateException? = null
        try {
            NotificationCaptureStateMachine.requireValidTransition(CaptureState.PROCESSED, CaptureState.QUEUED)
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertTrue(thrown != null)
        assertTrue(thrown!!.message!!.contains("PROCESSED -> QUEUED"))
    }
}
