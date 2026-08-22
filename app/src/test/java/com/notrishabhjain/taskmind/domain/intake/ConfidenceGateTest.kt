package com.notrishabhjain.taskmind.domain.intake

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfidenceGateTest {

    private val gate = ConfidenceGate()

    @Test
    fun `threshold boundary auto-creates`() {
        assertEquals(ConfidenceDecision.AUTO_CREATE, gate.classify(0.75))
    }

    @Test
    fun `above threshold auto-creates`() {
        assertEquals(ConfidenceDecision.AUTO_CREATE, gate.classify(0.99))
    }

    @Test
    fun `just below auto-create threshold routes to review`() {
        assertEquals(ConfidenceDecision.SEND_TO_REVIEW, gate.classify(0.74))
    }

    @Test
    fun `reject boundary routes to review`() {
        assertEquals(ConfidenceDecision.SEND_TO_REVIEW, gate.classify(0.40))
    }

    @Test
    fun `below reject boundary rejects`() {
        assertEquals(ConfidenceDecision.REJECT, gate.classify(0.39))
    }

    @Test
    fun `zero confidence rejects`() {
        assertEquals(ConfidenceDecision.REJECT, gate.classify(0.0))
    }

    @Test
    fun `missing confidence routes to review`() {
        assertEquals(ConfidenceDecision.SEND_TO_REVIEW, gate.classify(null))
    }

    @Test
    fun `not-a-number confidence routes to review`() {
        assertEquals(ConfidenceDecision.SEND_TO_REVIEW, gate.classify(Double.NaN))
    }

    @Test
    fun `infinite confidence routes to review`() {
        assertEquals(ConfidenceDecision.SEND_TO_REVIEW, gate.classify(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `out of range confidence above one routes to review`() {
        assertEquals(ConfidenceDecision.SEND_TO_REVIEW, gate.classify(1.5))
    }

    @Test
    fun `negative confidence routes to review`() {
        assertEquals(ConfidenceDecision.SEND_TO_REVIEW, gate.classify(-0.1))
    }

    @Test
    fun `custom thresholds are respected`() {
        val strict = ConfidenceGate(
            ConfidenceThresholds(autoCreateAtOrAbove = 0.9, rejectBelow = 0.2)
        )
        assertEquals(ConfidenceDecision.SEND_TO_REVIEW, strict.classify(0.8))
        assertEquals(ConfidenceDecision.REJECT, strict.classify(0.19))
        assertEquals(ConfidenceDecision.AUTO_CREATE, strict.classify(0.9))
    }
}
