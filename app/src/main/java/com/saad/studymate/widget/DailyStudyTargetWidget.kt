package com.saad.studymate.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.saad.studymate.MainActivity
import com.saad.studymate.data.StudyMateDatabase
import com.saad.studymate.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

private val WIDGET_GREEN = Color(0xFF39D98A)
private val WIDGET_MINT = Color(0xFF6EE7C8)
private val WIDGET_MUTED = Color(0xFF9EACBC)
private val WIDGET_DIM = Color(0xFF536171)

/**
 * Minimal daily checklist + streak widget. A day counts only when at least one
 * task was planned for that date and every planned task is completed.
 */
class DailyStudyTargetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) {
            val database = StudyMateDatabase.get(context)
            val todayStart = startOfDay(0)
            val tomorrowStart = startOfDay(1)
            val historyStart = startOfDay(-370)
            val rangeEnd = startOfDay(8)
            val tasks = database.taskDao().getDueBetween(historyStart, rangeEnd)
            val grouped = tasks.groupBy { startOfDayFor(it.dueAtMillis) }
            val todayTasks = grouped[todayStart].orEmpty()
            val todayDone = todayTasks.isNotEmpty() && todayTasks.all { it.isCompleted }

            var streak = 0
            val cursor = Calendar.getInstance().apply {
                timeInMillis = if (todayDone) todayStart else startOfDay(-1)
            }
            while (streak < 370) {
                val dayTasks = grouped[startOfDayFor(cursor.timeInMillis)].orEmpty()
                if (dayTasks.isEmpty() || dayTasks.any { !it.isCompleted }) break
                streak++
                cursor.add(Calendar.DAY_OF_YEAR, -1)
            }

            val monday = Calendar.getInstance().apply {
                timeInMillis = todayStart
                val offset = (get(Calendar.DAY_OF_WEEK) + 5) % 7
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val marks = (0..6).map { index ->
                val day = Calendar.getInstance().apply {
                    timeInMillis = monday.timeInMillis
                    add(Calendar.DAY_OF_YEAR, index)
                }
                val start = startOfDayFor(day.timeInMillis)
                val dayTasks = grouped[start].orEmpty()
                val complete = dayTasks.isNotEmpty() && dayTasks.all { it.isCompleted }
                val isToday = start == todayStart
                val symbol = when {
                    complete -> "✓"
                    isToday -> "○"
                    dayTasks.isNotEmpty() -> "•"
                    else -> "·"
                }
                DayMark(
                    label = listOf("M", "T", "W", "T", "F", "S", "S")[index],
                    symbol = symbol,
                    color = when {
                        complete -> WIDGET_GREEN
                        isToday -> WIDGET_MINT
                        dayTasks.isNotEmpty() -> WIDGET_MUTED
                        else -> WIDGET_DIM
                    }
                )
            }
            WidgetSnapshot(
                streak = streak,
                planned = todayTasks.size,
                completed = todayTasks.count { it.isCompleted },
                todayDone = todayDone,
                marks = marks,
                tomorrowPlanned = grouped[tomorrowStart].orEmpty().size
            )
        }

        val openAppIntent = Intent(context, MainActivity::class.java)
        provideContent { DailyStudyTargetContent(snapshot, openAppIntent) }
    }

    companion object {
        suspend fun refresh(context: Context) {
            DailyStudyTargetWidget().updateAll(context.applicationContext)
        }
    }
}

class DailyStudyTargetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyStudyTargetWidget()
}

private data class WidgetSnapshot(
    val streak: Int,
    val planned: Int,
    val completed: Int,
    val todayDone: Boolean,
    val marks: List<DayMark>,
    val tomorrowPlanned: Int
)

private data class DayMark(val label: String, val symbol: String, val color: Color)

@androidx.compose.runtime.Composable
private fun DailyStudyTargetContent(snapshot: WidgetSnapshot, openAppIntent: Intent) {
    val completed = snapshot.planned > 0 && snapshot.completed == snapshot.planned
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF151C27)))
            .clickable(actionStartActivity(openAppIntent))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.Start
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "🔥",
                style = TextStyle(fontSize = 25.sp)
            )
            Column(modifier = GlanceModifier.padding(start = 9.dp)) {
                Text(
                    text = "STREAK",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9EACBC)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(
                        text = snapshot.streak.toString(),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (snapshot.streak == 1) " DAY" else " DAYS",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFE1E8F0)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = if (completed) "✓ DONE" else "DAILY GOAL",
                style = TextStyle(
                    color = ColorProvider(if (completed) Color(0xFF39D98A) else Color(0xFF9EACBC)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(GlanceModifier.height(9.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            snapshot.marks.forEach { mark ->
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(
                        text = mark.label,
                        style = TextStyle(color = ColorProvider(Color(0xFF9EACBC)), fontSize = 9.sp)
                    )
                    Text(
                        text = mark.symbol,
                        style = TextStyle(
                            color = ColorProvider(mark.color),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(7.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = when {
                    snapshot.planned == 0 -> "TODAY'S PLAN"
                    completed -> "ALL TARGETS COMPLETE"
                    else -> "TODAY'S TARGETS"
                },
                style = TextStyle(
                    color = ColorProvider(Color(0xFF9EACBC)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = if (snapshot.planned == 0) "—" else "${snapshot.completed}/${snapshot.planned}",
                style = TextStyle(
                    color = ColorProvider(if (completed) Color(0xFF39D98A) else Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        val progressBlocks = if (snapshot.planned == 0) 0 else
            ((snapshot.completed.toFloat() / snapshot.planned) * 16).toInt().coerceIn(0, 16)
        Text(
            text = if (snapshot.planned == 0) "Add tasks for today in StudyMate" else
                "▰".repeat(progressBlocks) + "▱".repeat(16 - progressBlocks),
            style = TextStyle(
                color = ColorProvider(Color(0xFF39D98A)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
        if (snapshot.tomorrowPlanned > 0) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "Tomorrow · ${snapshot.tomorrowPlanned} planned",
                style = TextStyle(color = ColorProvider(Color(0xFF9EACBC)), fontSize = 9.sp)
            )
        }
    }
}

private fun startOfDay(offset: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, offset)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfDayFor(timeMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
