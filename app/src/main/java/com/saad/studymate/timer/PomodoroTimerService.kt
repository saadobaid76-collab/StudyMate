package com.saad.studymate.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.saad.studymate.MainActivity
import com.saad.studymate.R
import com.saad.studymate.data.StudyMateDatabase
import com.saad.studymate.data.StudySessionEntity
import com.saad.studymate.widget.DailyStudyTargetWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class PomodoroTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ticker = object : Runnable {
        override fun run() {
            val state = TimerStore.snapshot(this@PomodoroTimerService)
            if (!state.isRunning) {
                updateNotification(state)
                return
            }
            if (state.remainingMillis <= 0L) {
                completeSession(state)
                return
            }
            updateNotification(state)
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 25).coerceIn(1, 180)
                val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FOCUS
                val subjectId = if (intent.hasExtra(EXTRA_SUBJECT_ID)) intent.getLongExtra(EXTRA_SUBJECT_ID, -1L).takeIf { it >= 0L } else null
                TimerStore.start(this, minutes * 60_000L, mode, subjectId)
                startAsForeground(TimerStore.snapshot(this))
                startTicker()
            }
            ACTION_PAUSE -> {
                TimerStore.pause(this)
                handler.removeCallbacks(ticker)
                startAsForeground(TimerStore.snapshot(this))
            }
            ACTION_RESUME -> {
                TimerStore.resume(this)
                startAsForeground(TimerStore.snapshot(this))
                startTicker()
            }
            ACTION_RESET -> {
                handler.removeCallbacks(ticker)
                TimerStore.clear(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            else -> {
                val state = TimerStore.snapshot(this)
                if (state.isRunning || state.isPaused) startAsForeground(state)
                if (state.isRunning) startTicker()
                else if (!state.isPaused) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun completeSession(state: TimerSnapshot) {
        handler.removeCallbacks(ticker)
        val startedAt = state.startedAtMillis.takeIf { it > 0L }
            ?: (System.currentTimeMillis() - state.durationMillis)
        val completedMinutes = (state.durationMillis / 60_000L).toInt().coerceAtLeast(1)
        val isFocus = state.mode == MODE_FOCUS
        serviceScope.launch {
            val database = StudyMateDatabase.get(applicationContext)
            database.sessionDao().insert(
                StudySessionEntity(
                    subjectId = if (isFocus) state.subjectId else null,
                    focusMinutes = if (isFocus) completedMinutes else 0,
                    breakMinutes = if (isFocus) 0 else completedMinutes,
                    startedAtMillis = startedAt,
                    endedAtMillis = System.currentTimeMillis()
                )
            )
            if (isFocus) {
                state.subjectId?.let { subjectId ->
                    val weekStart = Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) add(Calendar.DAY_OF_MONTH, -1)
                    }.timeInMillis
                    database.weeklyGoalDao().addProgress(subjectId, weekStart, completedMinutes)
                }
            }
            DailyStudyTargetWidget.refresh(applicationContext)
        }
        TimerStore.clear(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, finishedNotification())
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun startAsForeground(state: TimerSnapshot) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: TimerSnapshot) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: TimerSnapshot): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 100, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val action = if (state.isRunning) ACTION_PAUSE else ACTION_RESUME
        val actionLabel = if (state.isRunning) "Pause" else "Resume"
        val actionPendingIntent = PendingIntent.getService(
            this, 101, Intent(this, PomodoroTimerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (state.mode == MODE_FOCUS) "StudyMate · Focus session" else "StudyMate · ${state.mode}"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymate)
            .setContentTitle(title)
            .setContentText(if (state.isPaused) "Timer paused" else "Your study timer is active")
            .setContentIntent(openPendingIntent)
            .setOngoing(state.isRunning || state.isPaused)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, actionLabel, actionPendingIntent)
        if (state.isRunning) {
            builder.setWhen(SystemClock.elapsedRealtime() + state.remainingMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        return builder.build()
    }

    private fun finishedNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_studymate)
        .setContentTitle("Study session complete")
        .setContentText("Nice work. Your focus time has been saved locally.")
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Study Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Ongoing countdown for a StudyMate focus session" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The timer state and elapsed-realtime deadline are persisted. The foreground
        // service continues while the app task is removed.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.saad.studymate.timer.START"
        const val ACTION_PAUSE = "com.saad.studymate.timer.PAUSE"
        const val ACTION_RESUME = "com.saad.studymate.timer.RESUME"
        const val ACTION_RESET = "com.saad.studymate.timer.RESET"
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_SUBJECT_ID = "extra_subject_id"
        const val MODE_FOCUS = "Focus"
        const val MODE_SHORT_BREAK = "Short Break"
        const val MODE_LONG_BREAK = "Long Break"
        private const val CHANNEL_ID = "studymate_timer"
        private const val NOTIFICATION_ID = 4107

        fun start(context: Context, minutes: Int, mode: String, subjectId: Long?) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MINUTES, minutes)
                putExtra(EXTRA_MODE, mode)
                if (subjectId != null) putExtra(EXTRA_SUBJECT_ID, subjectId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun command(context: Context, action: String) {
            val intent = Intent(context, PomodoroTimerService::class.java).setAction(action)
            if (action == ACTION_RESET) context.startService(intent)
            else ContextCompat.startForegroundService(context, intent)
        }
    }
}
