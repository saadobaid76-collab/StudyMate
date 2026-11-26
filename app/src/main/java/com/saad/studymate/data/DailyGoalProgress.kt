package com.saad.studymate.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Daily-goal rules shared by the app dashboard, launcher widget and reminder. */
data class DailyGoalDay(
    val startMillis: Long,
    val plannedCount: Int,
    val completedCount: Int
) {
    val isCompleted: Boolean
        get() = plannedCount > 0 && completedCount == plannedCount

    val pendingCount: Int
        get() = (plannedCount - completedCount).coerceAtLeast(0)
}

object DailyGoalProgress {
    fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun addDays(dayStartMillis: Long, amount: Int): Long = Calendar.getInstance().apply {
        timeInMillis = dayStartMillis
        add(Calendar.DAY_OF_MONTH, amount)
    }.timeInMillis

    fun summaryFor(tasks: List<TaskEntity>, dayMillis: Long): DailyGoalDay {
        val start = startOfDay(dayMillis)
        val end = addDays(start, 1)
        val planned = tasks.filter { it.dueAtMillis >= start && it.dueAtMillis < end }
        val completed = planned.count { task ->
            task.isCompleted && (
                task.completedAtMillis == 0L ||
                    (task.completedAtMillis >= start && task.completedAtMillis < end)
                )
        }
        return DailyGoalDay(start, planned.size, completed)
    }

    /** Monday-to-Sunday summaries, using the device's local calendar and timezone. */
    fun currentWeek(tasks: List<TaskEntity>, todayMillis: Long = System.currentTimeMillis()): List<DailyGoalDay> {
        val today = startOfDay(todayMillis)
        val monday = Calendar.getInstance().apply {
            timeInMillis = today
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis
        return (0..6).map { offset -> summaryFor(tasks, addDays(monday, offset)) }
    }

    /** Today's unfinished plan does not break the streak until the next day begins. */
    fun currentStreak(tasks: List<TaskEntity>, nowMillis: Long = System.currentTimeMillis()): Int {
        val today = startOfDay(nowMillis)
        val todaySummary = summaryFor(tasks, today)
        var cursor = if (todaySummary.isCompleted) today else addDays(today, -1)
        var streak = 0
        while (summaryFor(tasks, cursor).isCompleted) {
            streak++
            cursor = addDays(cursor, -1)
        }
        return streak
    }

    fun weekdayLabel(dayStartMillis: Long): String =
        SimpleDateFormat("EE", Locale.getDefault()).format(Date(dayStartMillis))
}
