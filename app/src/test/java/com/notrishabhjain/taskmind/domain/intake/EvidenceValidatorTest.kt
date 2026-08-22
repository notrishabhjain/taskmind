package com.notrishabhjain.taskmind.domain.intake

import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceValidatorTest {

    @Test
    fun `exact evidence present in source is valid`() {
        val result = EvidenceValidator.validate(
            evidence = "call mom tomorrow",
            sourceText = "hey please call mom tomorrow after work"
        )
        assertEquals(EvidenceCheck.Valid, result)
    }

    @Test
    fun `evidence matching ignoring case and whitespace is valid`() {
        val result = EvidenceValidator.validate(
            evidence = "  CALL   Mom ",
            sourceText = "call\tmom\nat 5pm"
        )
        assertEquals(EvidenceCheck.Valid, result)
    }

    @Test
    fun `evidence absent from source is rejected`() {
        val result = EvidenceValidator.validate(
            evidence = "buy groceries",
            sourceText = "remind me to call mom"
        )
        org.junit.Assert.assertTrue(result is EvidenceCheck.NotInSource)
    }

    @Test
    fun `paraphrased evidence is never repaired`() {
        val result = EvidenceValidator.validate(
            evidence = "phone mother",
            sourceText = "call mom now"
        )
        org.junit.Assert.assertTrue(result !is EvidenceCheck.Valid)
    }

    @Test
    fun `empty evidence is rejected`() {
        val result = EvidenceValidator.validate(evidence = "", sourceText = "some text")
        assertEquals(EvidenceCheck.EmptyEvidence, result)
    }

    @Test
    fun `blank whitespace-only evidence is rejected`() {
        val result = EvidenceValidator.validate(evidence = "   ", sourceText = "some text")
        assertEquals(EvidenceCheck.EmptyEvidence, result)
    }

    @Test
    fun `null evidence is rejected`() {
        val result = EvidenceValidator.validate(evidence = null, sourceText = "some text")
        assertEquals(EvidenceCheck.EmptyEvidence, result)
    }

    @Test
    fun `missing source rejects non-empty evidence`() {
        val result = EvidenceValidator.validate(evidence = "call mom", sourceText = null)
        assertEquals(EvidenceCheck.MissingSource, result)
    }

    @Test
    fun `blank source rejects non-empty evidence`() {
        val result = EvidenceValidator.validate(evidence = "call mom", sourceText = "   ")
        assertEquals(EvidenceCheck.MissingSource, result)
    }
}
