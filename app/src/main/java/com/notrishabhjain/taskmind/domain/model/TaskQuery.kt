package com.notrishabhjain.taskmind.domain.model

import java.time.Instant
import java.time.ZoneId

enum class TaskView { TODAY, UPCOMING, OVERDUE, COMPLETED, ARCHIVED, ALL }

enum class TaskSort { DUE_DATE, PRIORITY, CREATED }

data class TaskQuery(
    val view: TaskView = TaskView.TODAY,
    val sort: TaskSort = TaskSort.DUE_DATE,
    val search: String = ""
)

data class DayWindow(val start: Instant, val end: Instant)

object TaskTimeWindows {

    fun dayBounds(now: Instant, zone: ZoneId): DayWindow {
        val today = now.atZone(zone).toLocalDate()
        return DayWindow(
            start = today.atStartOfDay(zone).toInstant(),
            end = today.plusDays(1).atStartOfDay(zone).toInstant()
        )
    }
}
