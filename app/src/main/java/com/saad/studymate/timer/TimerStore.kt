package com.saad.studymate.timer

import android.content.Context
import android.os.SystemClock

private const val PREFS_NAME = "pomodoro_runtime"

 data class TimerSnapshot(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val mode: String = "Focus",
    val durationMillis: Long = 25 * 60_000L,
    val remainingMillis: Long = 25 * 60_000L,
    val subjectId: Long? = null,
    val startedAtMillis: Long = 0L
)

object TimerStore {
    private const val KEY_RUNNING = "running"
    private const val KEY_PAUSED = "paused"
    private const val KEY_MODE = "mode"
    private const val KEY_DURATION = "duration"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_END_ELAPSED = "end_elapsed"
    private const val KEY_SUBJECT = "subject_id"
    private const val KEY_STARTED_AT = "started_at"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(context: Context): TimerSnapshot {
        val p = prefs(context)
        val running = p.getBoolean(KEY_RUNNING, false)
        val paused = p.getBoolean(KEY_PAUSED, false)
        val storedRemaining = p.getLong(KEY_REMAINING, 25 * 60_000L)
        val remaining = if (running) {
            (p.getLong(KEY_END_ELAPSED, 0L) - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else storedRemaining
        val subject = if (p.contains(KEY_SUBJECT)) p.getLong(KEY_SUBJECT, -1L).takeIf { it >= 0L } else null
        return TimerSnapshot(
            isRunning = running,
            isPaused = paused,
            mode = p.getString(KEY_MODE, "Focus") ?: "Focus",
            durationMillis = p.getLong(KEY_DURATION, 25 * 60_000L),
            remainingMillis = remaining,
            subjectId = subject,
            startedAtMillis = p.getLong(KEY_STARTED_AT, 0L)
        )
    }

    fun start(context: Context, durationMillis: Long, mode: String, subjectId: Long?) {
        val now = SystemClock.elapsedRealtime()
        val editor = prefs(context).edit()
            .putBoolean(KEY_RUNNING, true)
            .putBoolean(KEY_PAUSED, false)
            .putString(KEY_MODE, mode)
            .putLong(KEY_DURATION, durationMillis)
            .putLong(KEY_REMAINING, durationMillis)
            .putLong(KEY_END_ELAPSED, now + durationMillis)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
        if (subjectId == null) editor.remove(KEY_SUBJECT) else editor.putLong(KEY_SUBJECT, subjectId)
        editor.apply()
    }

    fun pause(context: Context) {
        val p = prefs(context)
        val end = p.getLong(KEY_END_ELAPSED, 0L)
        val remaining = (end - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        p.edit().putBoolean(KEY_RUNNING, false).putBoolean(KEY_PAUSED, true)
            .putLong(KEY_REMAINING, remaining).apply()
    }

    fun resume(context: Context) {
        val p = prefs(context)
        val remaining = p.getLong(KEY_REMAINING, 0L).coerceAtLeast(0L)
        p.edit().putBoolean(KEY_RUNNING, true).putBoolean(KEY_PAUSED, false)
            .putLong(KEY_END_ELAPSED, SystemClock.elapsedRealtime() + remaining).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
