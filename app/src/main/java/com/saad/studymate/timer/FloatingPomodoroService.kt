package com.saad.studymate.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.saad.studymate.MainActivity
import com.saad.studymate.R
import kotlin.math.roundToInt

class FloatingPomodoroService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: LinearLayout? = null
    private var timerText: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {
        override fun run() {
            val state = TimerStore.snapshot(this@FloatingPomodoroService)

            timerText?.text = String.format(
                "%02d:%02d",
                state.remainingMillis / 60000,
                (state.remainingMillis % 60000) / 1000
            )

            if (floatingView != null) {
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startAsForeground()

        when (intent?.action) {
            ACTION_HIDE -> {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> {
                if (Settings.canDrawOverlays(this)) {
                    showOverlay()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (floatingView != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xFF172438.toInt())
            elevation = 12f
        }

        val heading = TextView(this).apply {
            text = "StudyMate • Focus"
            textSize = 13f
            setTextColor(0xFF6EE7C8.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }

        timerText = TextView(this).apply {
            textSize = 30f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun addButton(label: String, action: () -> Unit) {
            val button = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(12, 10, 12, 10)
                setOnClickListener { action() }
            }
            controls.addView(button)
        }

        addButton("Pause") {
            val state = TimerStore.snapshot(this)

            if (state.isRunning) {
                PomodoroTimerService.command(
                    this,
                    PomodoroTimerService.ACTION_PAUSE
                )
            } else if (state.isPaused) {
                PomodoroTimerService.command(
                    this,
                    PomodoroTimerService.ACTION_RESUME
                )
            }
        }

        addButton("Reset") {
            PomodoroTimerService.command(
                this,
                PomodoroTimerService.ACTION_RESET
            )
        }

        addButton("Close") {
            removeOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        root.addView(heading)
        root.addView(timerText)
        root.addView(controls)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }

        // Drag the floating timer using its heading.
        heading.setOnTouchListener(object : View.OnTouchListener {

            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(
                view: View,
                event: MotionEvent
            ): Boolean {

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = layoutParams.x
                        startY = layoutParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x =
                            startX + (event.rawX - touchX).roundToInt()

                        layoutParams.y =
                            startY + (event.rawY - touchY).roundToInt()

                        try {
                            windowManager.updateViewLayout(
                                root,
                                layoutParams
                            )
                        } catch (_: Exception) {
                        }

                        return true
                    }

                    MotionEvent.ACTION_UP -> return true
                }

                return false
            }
        })

        floatingView = root
        params = layoutParams

        try {
            windowManager.addView(root, layoutParams)
            handler.post(updater)
        } catch (_: Exception) {
            floatingView = null
            timerText = null
            params = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(updater)

        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }

        floatingView = null
        timerText = null
        params = null
    }

    private fun startAsForeground() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            501,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymate)
            .setContentTitle("StudyMate Floating Timer")
            .setContentText("Floating study timer is active")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating Pomodoro",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {

        const val ACTION_SHOW =
            "com.saad.studymate.timer.FLOAT_SHOW"

        const val ACTION_HIDE =
            "com.saad.studymate.timer.FLOAT_HIDE"

        private const val CHANNEL_ID = "studymate_floating_timer"
        private const val NOTIFICATION_ID = 4108

        fun show(context: Context) {
            val intent = Intent(
                context,
                FloatingPomodoroService::class.java
            ).setAction(ACTION_SHOW)

            ContextCompat.startForegroundService(context, intent)
        }

        fun hide(context: Context) {
            val intent = Intent(
                context,
                FloatingPomodoroService::class.java
            ).setAction(ACTION_HIDE)

            ContextCompat.startForegroundService(context, intent)
        }
    }
}
