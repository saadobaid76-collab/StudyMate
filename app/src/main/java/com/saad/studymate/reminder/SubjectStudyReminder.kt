
package com.saad.studymate.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saad.studymate.MainActivity
import com.saad.studymate.R
import java.util.Calendar

/**
 * Subject-wise reminder.
 * Sends a notification 20 minutes before the scheduled study time.
 * Weekday mask: Calendar.SUNDAY = 1 ... Calendar.SATURDAY = 7.
 */
object SubjectStudyReminder {

    const val CHANNEL_ID = "subject_study_reminders"
    private const val PREFS = "subject_study_reminder_settings"
    private const val PREFIX = "subject_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Schedule(
        val subjectId: Long,
        val subjectName: String,
        val hour: Int,
        val minute: Int,
        val daysMask: Int,
        val enabled: Boolean
    )

    fun save(
        context: Context,
        subjectId: Long,
        subjectName: String,
        hour: Int,
        minute: Int,
        daysMask: Int,
        enabled: Boolean
    ) {
        require(hour in 0..23 && minute in 0..59)

        val key = PREFIX + subjectId
        prefs(context).edit()
            .putString("${key}_name", subjectName)
            .putInt("${key}_hour", hour)
            .putInt("${key}_minute", minute)
            .putInt("${key}_days", daysMask)
            .putBoolean("${key}_enabled", enabled)
            .apply()

        scheduleNext(context, subjectId)
    }

    fun getSchedule(context: Context, subjectId: Long): Schedule? {
        val p = prefs(context)
        val key = PREFIX + subjectId

        if (!p.contains("${key}_name")) return null

        return Schedule(
            subjectId = subjectId,
            subjectName = p.getString("${key}_name", "Study") ?: "Study",
            hour = p.getInt("${key}_hour", 18),
            minute = p.getInt("${key}_minute", 0),
            daysMask = p.getInt("${key}_days", 127),
            enabled = p.getBoolean("${key}_enabled", false)
        )
    }

    fun scheduleAll(context: Context) {
        val p = prefs(context)
        p.all.keys
            .filter { it.startsWith(PREFIX) && it.endsWith("_name") }
            .mapNotNull { it.removePrefix(PREFIX).removeSuffix("_name").toLongOrNull() }
            .forEach { scheduleNext(context, it) }
    }

    fun scheduleNext(context: Context, subjectId: Long) {
        val schedule = getSchedule(context, subjectId) ?: return
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pendingIntent = pendingIntent(context, schedule)

        alarmManager.cancel(pendingIntent)

        if (!schedule.enabled || schedule.daysMask == 0) return

        val now = System.currentTimeMillis()
        var nextTrigger: Long? = null

        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, schedule.hour)
                set(Calendar.MINUTE, schedule.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, -20)
            }

            val dayBit = 1 shl (candidate.get(Calendar.DAY_OF_WEEK) - 1)

            if ((schedule.daysMask and dayBit) != 0 &&
                candidate.timeInMillis > now
            ) {
                nextTrigger = candidate.timeInMillis
                break
            }
        }

        nextTrigger?.let {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                it,
                pendingIntent
            )
        }
    }

    private fun pendingIntent(
        context: Context,
        schedule: Schedule
    ): PendingIntent {
        val intent = Intent(context, SubjectStudyReminderReceiver::class.java).apply {
            putExtra("subject_id", schedule.subjectId)
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode(schedule.subjectId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(subjectId: Long): Int =
        (subjectId xor (subjectId ushr 32)).toInt() and 0x7fffffff

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Subject study reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminds you before each subject's scheduled study time."
            }

            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    internal fun showNotification(context: Context, subjectId: Long) {
        val schedule = getSchedule(context, subjectId) ?: return

        if (!schedule.enabled || !notificationsAllowed(context)) return

        createChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            requestCode(subjectId) + 10000,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = "তোমার ${schedule.subjectName} পড়ার সময় হয়ে যাচ্ছে! চলে আসো!"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_study)
            .setContentTitle("Study time is approaching 📚")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(requestCode(subjectId), notification)
        }
    }

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}

class SubjectStudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val subjectId = intent?.getLongExtra("subject_id", -1L) ?: -1L
        if (subjectId < 0L) return

        SubjectStudyReminder.showNotification(context, subjectId)

        // Schedule the next matching weekday.
        SubjectStudyReminder.scheduleNext(context, subjectId)
    }
}

class SubjectStudyReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            SubjectStudyReminder.scheduleAll(context)
        }
    }
}
