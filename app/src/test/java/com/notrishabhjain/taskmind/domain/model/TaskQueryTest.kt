package com.notrishabhjain.taskmind.domain.model

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTimeWindowsTest {

    @Test
    fun `utc day bounds cover the calendar day`() {
        val now = Instant.parse("2026-08-22T15:30:00Z")

        val window = TaskTimeWindows.dayBounds(now, ZoneId.of("UTC"))

        assertEquals(Instant.parse("2026-08-22T00:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-08-23T00:00:00Z"), window.end)
    }

    @Test
    fun `midnight instant equals window start inclusively`() {
        val midnight = Instant.parse("2026-08-22T00:00:00Z")

        val window = TaskTimeWindows.dayBounds(midnight, ZoneId.of("UTC"))

        assertEquals(window.start, midnight)
        assertTrueInclusive(window, midnight)
    }

    @Test
    fun `offset zone maps local calendar day back to utc instants`() {
        val zone = ZoneId.of("+05:30")
        val now = Instant.parse("2026-08-22T20:00:00Z")
        val localDate = now.atZone(zone).toLocalDate()

        val window = TaskTimeWindows.dayBounds(now, zone)

        assertEquals(localDate.atStartOfDay(zone).toInstant(), window.start)
        assertEquals(localDate.plusDays(1).atStartOfDay(zone).toInstant(), window.end)
    }

    private fun assertTrueInclusive(window: DayWindow, instant: Instant) {
        val inside = !instant.isBefore(window.start) && instant.isBefore(window.end)
        org.junit.Assert.assertTrue(inside)
    }
}
