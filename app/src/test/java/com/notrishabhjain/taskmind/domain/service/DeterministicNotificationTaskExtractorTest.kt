package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.intake.EvidenceCheck
import com.notrishabhjain.taskmind.domain.intake.EvidenceValidator
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicNotificationTaskExtractorTest {

    private val extractor = DeterministicNotificationTaskExtractor()
    private val zone = ZoneId.of("UTC")

    private val now: Instant = ZonedDateTime.of(2026, 8, 24, 10, 0, 0, 0, zone).toInstant()

    private fun capture(canonical: String): NotificationCapture = NotificationCapture(
        idempotencyKey = "k-${canonical.hashCode()}",
        sourcePackage = "com.example.app",
        notificationKey = "n1",
        postTime = now,
        canonicalSourceText = canonical,
        contentHash = "hash",
        sourceRef = "notification:com.example.app:n1",
        createdAt = now,
        updatedAt = now
    )

    private fun extract(canonical: String): NotificationExtractionOutcome =
        extractor.extract(capture(canonical), now, zone)

    @Test
    fun `call with tomorrow and time reaches auto-create band`() {
        val outcome = extract("Please call Rahul tomorrow at 4 PM") as NotificationExtractionOutcome.Actionable
        assertEquals("Call Rahul", outcome.extraction.title)
        assertEquals(2026, ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone).year)
        assertEquals(0.75, outcome.extraction.confidence, 0.001)
        assertTrue(outcome.extraction.reasoning.contains("action 'call'"))
        assertTrue(outcome.extraction.reasoning.contains("due 'tomorrow at 4 pm'"))
        assertEquals(DeterministicNotificationTaskExtractor.MODEL_ID, outcome.extraction.modelId)
    }

    @Test
    fun `evidence is always a verbatim span of the canonical text`() {
        val texts = listOf(
            "Please call Rahul tomorrow at 4 PM",
            "Remind me to submit the report by Friday",
            "Your electricity bill of ₹2,450 is due tomorrow.",
            "Meeting moved to Friday at 3 PM."
        )
        for (text in texts) {
            val outcome = extract(text) as NotificationExtractionOutcome.Actionable
            val check = EvidenceValidator.validate(
                evidence = outcome.extraction.evidence,
                sourceText = text
            )
            assertEquals("evidence for: $text", EvidenceCheck.Valid, check)
        }
    }

    @Test
    fun `reminder language with weekday produces high confidence task`() {
        val outcome = extract("Remind me to submit the report by Friday") as NotificationExtractionOutcome.Actionable
        assertEquals("Submit the report", outcome.extraction.title)
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(2026, due.year); assertEquals(8, due.monthValue); assertEquals(28, due.dayOfMonth)
        assertEquals(9, due.hour)
        assertEquals(0.90, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `meeting move without action verb routes in review band`() {
        val outcome = extract("Meeting moved to Friday at 3 PM.") as NotificationExtractionOutcome.Actionable
        assertEquals("Meeting moved", outcome.extraction.title)
        assertEquals(15, ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone).hour)
        assertEquals(0.70, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `dentist reminder picks due from neighbouring sentence`() {
        val outcome = extract("Dentist appointment reminder. Tomorrow 4:00 PM.") as NotificationExtractionOutcome.Actionable
        assertEquals("Dentist appointment reminder", outcome.extraction.title)
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(25, due.dayOfMonth); assertEquals(16, due.hour)
        assertEquals(0.95, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `otp notifications are never actionable`() {
        val outcome = extract("Your OTP is 482913.")
        assertTrue(outcome is NotificationExtractionOutcome.NotActionable)
    }

    @Test
    fun `verification code with digits is never actionable`() {
        val outcome = extract("G-849201 is your Google verification code")
        assertTrue(outcome is NotificationExtractionOutcome.NotActionable)
    }

    @Test
    fun `promotional sale notifications are rejected`() {
        val outcome = extract("SALE! Flat 50% off today.")
        assertTrue(outcome is NotificationExtractionOutcome.NotActionable)
    }

    @Test
    fun `bill due notifications become pay tasks in review band`() {
        val outcome = extract("Your electricity bill of ₹2,450 is due tomorrow.") as NotificationExtractionOutcome.Actionable
        assertEquals("Pay electricity bill", outcome.extraction.title)
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(25, due.dayOfMonth); assertEquals(9, due.hour)
        assertEquals(0.55, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `balance updates are informational`() {
        val outcome = extract("Your account balance is ₹500.")
        assertTrue(outcome is NotificationExtractionOutcome.NotActionable)
    }

    @Test
    fun `security alerts are informational`() {
        val outcome = extract("Security alert: new device sign-in on your account")
        assertTrue(outcome is NotificationExtractionOutcome.NotActionable)
    }

    @Test
    fun `delivered parcels are informational`() {
        val outcome = extract("Your parcel has been delivered.")
        assertTrue(outcome is NotificationExtractionOutcome.NotActionable)
    }

    @Test
    fun `question form lowers confidence`() {
        val outcome = extract("Can you check the report?") as NotificationExtractionOutcome.Actionable
        assertEquals(0.20, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `very short content lowers confidence`() {
        val outcome = extract("Call mom") as NotificationExtractionOutcome.Actionable
        assertEquals("Call mom", outcome.extraction.title)
        assertEquals(0.30, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `empty canonical content is not actionable`() {
        val outcome = extract("   ")
        assertTrue(outcome is NotificationExtractionOutcome.NotActionable)
    }

    @Test
    fun `today resolves to same day default morning`() {
        val outcome = extract("Submit expense report today") as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(24, due.dayOfMonth); assertEquals(9, due.hour)
    }

    @Test
    fun `plain weekday skips to next future occurrence`() {
        val outcome = extract("Call bank on monday") as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(31, due.dayOfMonth); assertEquals(8, due.monthValue)
    }

    @Test
    fun `next weekday lands a week beyond the next occurrence`() {
        val outcome = extract("Call bank next monday") as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(7, due.dayOfMonth); assertEquals(9, due.monthValue)
    }

    @Test
    fun `day-month date form parses`() {
        val outcome = extract("Submit assignment by 25 aug") as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(25, due.dayOfMonth); assertEquals(8, due.monthValue)
    }

    @Test
    fun `explicit year is honoured`() {
        val outcome = extract("Renew passport by aug 25 2027") as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(2027, due.year); assertEquals(8, due.monthValue); assertEquals(25, due.dayOfMonth)
    }

    @Test
    fun `past explicit dates roll to next year`() {
        val outcome = extract("Book table for jan 5") as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(2027, due.year); assertEquals(1, due.monthValue)
    }

    @Test
    fun `tonight defaults to eight pm`() {
        val outcome = extract("Call mom tonight") as NotificationExtractionOutcome.Actionable
        assertEquals(20, ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone).hour)
    }

    @Test
    fun `noon parses as twelve`() {
        val outcome = extract("Attend standup at noon tomorrow") as NotificationExtractionOutcome.Actionable
        assertEquals(12, ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone).hour)
        assertEquals(0.75, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `24h clock time parses`() {
        val outcome = extract("Call dentist at 18:30") as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(18, due.hour); assertEquals(30, due.minute)
        assertEquals(0.55, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `day after tomorrow adds two days`() {
        val outcome = extract("Book tickets day after tomorrow") as NotificationExtractionOutcome.Actionable
        assertEquals(26, ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone).dayOfMonth)
    }

    @Test
    fun `next week adds seven days`() {
        val outcome = extract("Finish report next week") as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(31, due.dayOfMonth); assertEquals(8, due.monthValue)
    }

    @Test
    fun `part of day sets default time without date weight`() {
        val outcome = extract("Check server logs this evening") as NotificationExtractionOutcome.Actionable
        assertEquals(18, ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone).hour)
        assertEquals(0.45, outcome.extraction.confidence, 0.001)
    }

    @Test
    fun `midnight crossing advances the date`() {
        val lateNight = ZonedDateTime.of(2026, 12, 31, 23, 0, 0, 0, zone).toInstant()
        val outcome = extractor.extract(capture("Call parents tomorrow"), lateNight, zone) as NotificationExtractionOutcome.Actionable
        val due = ZonedDateTime.ofInstant(outcome.extraction.dueAt!!, zone)
        assertEquals(2027, due.year); assertEquals(1, due.monthValue); assertEquals(1, due.dayOfMonth)
    }

    @Test
    fun `non utc zone shifts resolved local day`() {
        val apia = ZoneId.of("Pacific/Apia")
        val instant = ZonedDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        val outcome = extractor.extract(capture("Call mom tomorrow"), instant, apia) as NotificationExtractionOutcome.Actionable
        val local = outcome.extraction.dueAt!!.atZone(apia)
        assertEquals(26, local.dayOfMonth)
    }

    @Test
    fun `confidence stays within unit range for noisy input`() {
        val outcome = extract("!!! ... ???")
        when (outcome) {
            is NotificationExtractionOutcome.NotActionable -> Unit
            is NotificationExtractionOutcome.Actionable ->
                assertTrue(outcome.extraction.confidence in 0.0..1.0 && outcome.extraction.title.isNotBlank())
        }
    }

    @Test
    fun `url boilerplate does not leak into title or evidence`() {
        val text = "Please review the design document https://docs.google.com/x by friday"
        val outcome = extract(text) as NotificationExtractionOutcome.Actionable
        assertTrue(!outcome.extraction.title.contains("http"))
        assertTrue(outcome.extraction.title.startsWith("Review"))
        assertEquals(EvidenceCheck.Valid, EvidenceValidator.validate(outcome.extraction.evidence, text))
    }
}
