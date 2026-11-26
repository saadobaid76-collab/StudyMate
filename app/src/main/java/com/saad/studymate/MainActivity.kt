package com.saad.studymate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saad.studymate.ai.StudyIntelligenceEngine
import com.saad.studymate.data.QuizRecordEntity
import com.saad.studymate.data.StudyMateDatabase
import com.saad.studymate.data.WeeklyGoalEntity
import com.saad.studymate.data.StudyNoteEntity
import com.saad.studymate.data.StudyPreferences
import com.saad.studymate.data.SubjectEntity
import com.saad.studymate.data.TaskEntity
import com.saad.studymate.timer.PomodoroTimerService
import com.saad.studymate.timer.TimerSnapshot
import com.saad.studymate.timer.TimerStore
import com.saad.studymate.widget.DailyStudyTargetWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { StudyMateRoot() }
    }
}

private enum class StudyScreen(val label: String) {
    HOME("Home"), PLAN("Planner"), FOCUS("Focus"), COACH("AI Coach"), MORE("More")
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun StudyMateRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { StudyMateDatabase.get(context) }
    val preferences = remember { StudyPreferences(context) }
    val tasks by database.taskDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by database.sessionDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val subjects by database.subjectDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val quizzes by database.quizDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by database.noteDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val weekStart = remember { startOfWeekSaturdayMillis() }
    val weeklyGoals by database.weeklyGoalDao().observeForWeek(weekStart).collectAsStateWithLifecycle(initialValue = emptyList())
    val darkMode by preferences.darkMode.collectAsState(initial = true)
    var currentScreen by remember { mutableStateOf(StudyScreen.HOME) }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val scope = rememberCoroutineScope()

    // Keep the launcher widget in sync with Room whenever today's tasks or
    // completed study sessions change while the app is open.
    LaunchedEffect(tasks, sessions) {
        DailyStudyTargetWidget.refresh(context)
    }

    val darkColors = darkColorScheme(
        primary = Color(0xFF6EE7C8),
        onPrimary = Color(0xFF06251F),
        secondary = Color(0xFF92B8FF),
        tertiary = Color(0xFFFFB86B),
        background = Color(0xFF0D1421),
        surface = Color(0xFF151F30),
        surfaceVariant = Color(0xFF202D40),
        onSurface = Color(0xFFE8EEF7),
        onSurfaceVariant = Color(0xFFB2BDCD)
    )
    val lightColors = lightColorScheme(
        primary = Color(0xFF087E69),
        onPrimary = Color.White,
        secondary = Color(0xFF315F9E),
        tertiary = Color(0xFFB65D12),
        background = Color(0xFFF4F7FA),
        surface = Color.White,
        surfaceVariant = Color(0xFFE8EEF3),
        onSurface = Color(0xFF1A2733),
        onSurfaceVariant = Color(0xFF526170)
    )

    MaterialTheme(colorScheme = if (darkMode) darkColors else lightColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("StudyMate", fontWeight = FontWeight.Bold)
                            Text(currentScreen.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { preferences.setDarkMode(!darkMode) } }) {
                            Text(if (darkMode) "☀" else "☾", fontSize = 20.sp)
                        }
                    }
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        StudyScreen.entries.forEach { screen ->
                            NavigationBarItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = { ScreenIcon(screen) },
                                label = { Text(screen.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                if (wide) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        StudyScreen.entries.forEach { screen ->
                            NavigationRailItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = { ScreenIcon(screen) },
                                label = { Text(screen.label) }
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    AnimatedContent(targetState = currentScreen, label = "screen-transition") { screen ->
                        when (screen) {
                            StudyScreen.HOME -> HomeScreen(
                                tasks = tasks,
                                sessions = sessions,
                                subjects = subjects,
                                onToggleTask = { id, done -> scope.launch { database.taskDao().setCompleted(id, done) } },
                                onOpenPlanner = { currentScreen = StudyScreen.PLAN },
                                onOpenFocus = { currentScreen = StudyScreen.FOCUS }
                            )
                            StudyScreen.PLAN -> PlannerScreen(
                                tasks = tasks,
                                subjects = subjects,
                                weeklyGoals = weeklyGoals,
                                onSaveGoal = { subjectId, targetMinutes ->
                                    scope.launch { database.weeklyGoalDao().save(WeeklyGoalEntity(subjectId = subjectId, weekStartMillis = weekStart, targetMinutes = targetMinutes)) }
                                },
                                onAddTask = { title, subjectId, priority, dueAtMillis ->
                                    scope.launch {
                                        database.taskDao().insert(
                                            TaskEntity(
                                                title = title,
                                                subjectId = subjectId,
                                                dueAtMillis = dueAtMillis,
                                                priority = priority
                                            )
                                        )
                                    }
                                },
                                onToggleTask = { id, done -> scope.launch { database.taskDao().setCompleted(id, done) } },
                                onDeleteTask = { id -> scope.launch { database.taskDao().delete(id) } }
                            )
                            StudyScreen.FOCUS -> FocusScreen(subjects = subjects, preferences = preferences)
                            StudyScreen.COACH -> CoachScreen(tasks = tasks, sessions = sessions, subjects = subjects)
                            StudyScreen.MORE -> MoreScreen(
                                sessions = sessions,
                                subjects = subjects,
                                quizzes = quizzes,
                                notes = notes,
                                darkMode = darkMode,
                                onDarkModeChange = { enabled -> scope.launch { preferences.setDarkMode(enabled) } },
                                onAddQuiz = { subjectId, title, obtained, total, mistakes ->
                                    scope.launch {
                                        database.quizDao().insert(
                                            QuizRecordEntity(
                                                subjectId = subjectId,
                                                title = title,
                                                obtainedMarks = obtained,
                                                totalMarks = total,
                                                dateMillis = System.currentTimeMillis(),
                                                mistakeNotes = mistakes
                                            )
                                        )
                                    }
                                },
                                onAddNote = { subjectId, title, body ->
                                    scope.launch {
                                        database.noteDao().insert(
                                            StudyNoteEntity(title = title, body = body, subjectId = subjectId)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenIcon(screen: StudyScreen) {
    val icon = when (screen) {
        StudyScreen.HOME -> Icons.Outlined.Home
        StudyScreen.PLAN -> Icons.Outlined.Assignment
        StudyScreen.FOCUS -> Icons.Outlined.Timer
        StudyScreen.COACH -> Icons.Outlined.AutoAwesome
        StudyScreen.MORE -> Icons.Outlined.MoreHoriz
    }
    Icon(icon, contentDescription = screen.label)
}

@Composable
private fun HomeScreen(
    tasks: List<TaskEntity>,
    sessions: List<com.saad.studymate.data.StudySessionEntity>,
    subjects: List<SubjectEntity>,
    onToggleTask: (Long, Boolean) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenFocus: () -> Unit
) {
    val todayStart = remember { startOfTodayMillis() }
    val tomorrowStart = remember { startOfDayOffsetMillis(1) }
    val todayTasks = tasks.filter { it.dueAtMillis >= todayStart && it.dueAtMillis < tomorrowStart }
    val todayFocus = sessions.filter { it.startedAtMillis >= todayStart }.sumOf { it.focusMinutes }
    val completedToday = todayTasks.count { it.isCompleted }
    val pendingToday = todayTasks.count { !it.isCompleted }
    val goalStreak = dailyTaskStreak(tasks)
    val taskProgress = if (todayTasks.isEmpty()) 0f else completedToday.toFloat() / todayTasks.size
    val advice = remember(tasks, sessions, subjects) { StudyIntelligenceEngine.analyze(sessions, tasks, subjects) }

    ContentColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(modifier = Modifier.weight(1.15f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily goal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (todayTasks.isEmpty()) "Plan your day" else "$completedToday / ${todayTasks.size}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (todayTasks.isEmpty()) "Add today's study tasks" else if (pendingToday == 0) "All targets complete ✓" else "$pendingToday tasks remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { taskProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        strokeCap = StrokeCap.Round
                    )
                    Text("🔥  $goalStreak day streak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Focused", formatMinutes(todayFocus), "◷")
                StatCard("Left today", pendingToday.toString(), "☷")
                StatCard("Planned", todayTasks.size.toString(), "✓")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Today's AI study cue", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    AssistChip(onClick = onOpenFocus, label = { Text("Start focus") })
                }
                Text(advice.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(advice.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text("Suggested rhythm: ${advice.focusMinutes} min focus · ${advice.breakMinutes} min break", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today's tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenPlanner) { Text("See all") }
        }
        val visibleTasks = todayTasks.filter { !it.isCompleted }.take(4) + todayTasks.filter { it.isCompleted }.take(2)
        if (visibleTasks.isEmpty()) {
            EmptyState("Your task list is clear", "Add a task in Planner to give your next study session a direction.")
        } else {
            visibleTasks.forEach { task -> TaskRow(task, onToggleTask, null) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Need a focused start?", fontWeight = FontWeight.SemiBold)
                    Text("One session is enough to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onOpenFocus) { Text("Open timer") }
            }
        }
    }
}

@Composable
private fun PlannerScreen(
    tasks: List<TaskEntity>,
    subjects: List<SubjectEntity>,
    weeklyGoals: List<WeeklyGoalEntity>,
    onSaveGoal: (Long, Int) -> Unit,
    onAddTask: (String, Long?, Int, Long) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    ContentColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your study plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Keep the next action clear and achievable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { showAdd = true }) { Text("+ Task") }
        }
        val done = tasks.count { it.isCompleted }
        val ratio = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Task completion", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("$done / ${tasks.size}", color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = StrokeCap.Round)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly targets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showGoal = true }) { Text("+ Goal") }
        }
        if (weeklyGoals.isEmpty()) {
            Text("Set a weekly target for a subject. Completed focus sessions will add to its progress automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            weeklyGoals.forEach { goal ->
                val subject = subjects.firstOrNull { it.id == goal.subjectId }?.name ?: "Subject"
                val fraction = (goal.completedMinutes.toFloat() / goal.targetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(subject, fontWeight = FontWeight.SemiBold)
                            Text("${goal.completedMinutes}/${goal.targetMinutes} min", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(7.dp), strokeCap = StrokeCap.Round)
                    }
                }
            }
        }
        Text("All tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (tasks.isEmpty()) EmptyState("No tasks yet", "Add your first task and assign it to a subject.")
        tasks.forEach { task -> TaskRow(task, onToggleTask, onDeleteTask) }
    }
    if (showAdd) {
        AddTaskDialog(subjects = subjects, onDismiss = { showAdd = false }, onSave = { title, subject, priority, dueAtMillis ->
            onAddTask(title, subject, priority, dueAtMillis)
            showAdd = false
        })
    }
    if (showGoal) {
        AddWeeklyGoalDialog(subjects = subjects, onDismiss = { showGoal = false }, onSave = { subjectId, target ->
            onSaveGoal(subjectId, target)
            showGoal = false
        })
    }
}

@Composable
private fun FocusScreen(subjects: List<SubjectEntity>, preferences: StudyPreferences) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusDefault by preferences.focusMinutes.collectAsState(initial = 25)
    val shortDefault by preferences.shortBreakMinutes.collectAsState(initial = 5)
    val longDefault by preferences.longBreakMinutes.collectAsState(initial = 15)
    var mode by remember { mutableStateOf(TimerStore.snapshot(context).mode) }
    var focusMinutes by remember(focusDefault) { mutableIntStateOf(focusDefault) }
    var shortMinutes by remember(shortDefault) { mutableIntStateOf(shortDefault) }
    var longMinutes by remember(longDefault) { mutableIntStateOf(longDefault) }
    var selectedSubjectId by remember { mutableStateOf<Long?>(subjects.firstOrNull()?.id) }
    var subjectMenu by remember { mutableStateOf(false) }
    var timer by remember { mutableStateOf(TimerStore.snapshot(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            timer = TimerStore.snapshot(context)
            delay(500)
        }
    }
    LaunchedEffect(subjects) {
        if (selectedSubjectId == null) selectedSubjectId = subjects.firstOrNull()?.id
    }
    LaunchedEffect(timer.mode, timer.isRunning, timer.isPaused) {
        if (timer.isRunning || timer.isPaused) mode = timer.mode
    }

    val selectedDuration = when (mode) {
        PomodoroTimerService.MODE_SHORT_BREAK -> shortMinutes
        PomodoroTimerService.MODE_LONG_BREAK -> longMinutes
        else -> focusMinutes
    }
    val isActive = timer.isRunning || timer.isPaused
    val duration = if (isActive) timer.durationMillis else selectedDuration * 60_000L
    val remaining = if (isActive) timer.remainingMillis else duration
    val progress = if (duration <= 0L) 0f else (1f - remaining.toFloat() / duration).coerceIn(0f, 1f)
    val minutes = (remaining / 60_000L).toInt()
    val seconds = ((remaining % 60_000L) / 1_000L).toInt()

    ContentColumn {
        Text("Focus space", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("A simple timer that keeps counting while the screen is off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(PomodoroTimerService.MODE_FOCUS, PomodoroTimerService.MODE_SHORT_BREAK, PomodoroTimerService.MODE_LONG_BREAK).forEach { item ->
                FilterChip(
                    selected = mode == item,
                    onClick = { if (!isActive) mode = item },
                    label = { Text(item.replace(" Break", "")) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    val animated by animateFloatAsState(progress, tween(350), label = "timer-progress")
                    CircularProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 13.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d:%02d".format(Locale.US, minutes, seconds), fontSize = 43.sp, fontWeight = FontWeight.Bold)
                        Text(if (timer.isPaused) "Paused" else if (timer.isRunning) "Stay with it" else mode, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (!isActive) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        IconButton(onClick = {
                            when (mode) {
                                PomodoroTimerService.MODE_SHORT_BREAK -> shortMinutes = (shortMinutes - 1).coerceAtLeast(1)
                                PomodoroTimerService.MODE_LONG_BREAK -> longMinutes = (longMinutes - 1).coerceAtLeast(1)
                                else -> focusMinutes = (focusMinutes - 1).coerceAtLeast(1)
                            }
                        }) {
                            Text("−", fontSize = 27.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$selectedDuration min", fontWeight = FontWeight.SemiBold)
                            Text("Custom duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            when (mode) {
                                PomodoroTimerService.MODE_SHORT_BREAK -> shortMinutes = (shortMinutes + 1).coerceAtMost(60)
                                PomodoroTimerService.MODE_LONG_BREAK -> longMinutes = (longMinutes + 1).coerceAtMost(90)
                                else -> focusMinutes = (focusMinutes + 1).coerceAtMost(180)
                            }
                        }) {
                            Text("+", fontSize = 24.sp)
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { subjectMenu = true }) {
                            Text(subjects.firstOrNull { it.id == selectedSubjectId }?.name ?: "Choose subject")
                        }
                        DropdownMenu(expanded = subjectMenu, onDismissRequest = { subjectMenu = false }) {
                            subjects.forEach { subject ->
                                DropdownMenuItem(
                                    text = { Text(subject.name) },
                                    onClick = { selectedSubjectId = subject.id; subjectMenu = false }
                                )
                            }
                        }
                    }
                } else {
                    Text("${timer.mode} · ${subjects.firstOrNull { it.id == timer.subjectId }?.name ?: "Study session"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        when {
                            timer.isRunning -> PomodoroTimerService.command(context, PomodoroTimerService.ACTION_PAUSE)
                            timer.isPaused -> PomodoroTimerService.command(context, PomodoroTimerService.ACTION_RESUME)
                            else -> PomodoroTimerService.start(context, selectedDuration, mode, if (mode == PomodoroTimerService.MODE_FOCUS) selectedSubjectId else null)
                        }
                        timer = TimerStore.snapshot(context)
                    }) {
                        Icon(if (timer.isRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (timer.isRunning) "Pause" else if (timer.isPaused) "Resume" else "Start")
                    }
                    OutlinedButton(onClick = {
                        PomodoroTimerService.command(context, PomodoroTimerService.ACTION_RESET)
                        timer = TimerSnapshot()
                    }, enabled = isActive) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reset")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Timer tip", fontWeight = FontWeight.SemiBold)
                    Text("A completed focus timer is saved to your local study history. Keep notifications enabled to see the countdown outside the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    LaunchedEffect(focusMinutes, shortMinutes, longMinutes) {
        preferences.setDurations(focusMinutes, shortMinutes, longMinutes)
    }
}


@Composable
private fun CoachScreen(
    tasks: List<TaskEntity>,
    sessions: List<com.saad.studymate.data.StudySessionEntity>,
    subjects: List<SubjectEntity>
) {
    val advice = remember(tasks, sessions, subjects) { StudyIntelligenceEngine.analyze(sessions, tasks, subjects) }
    ContentColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp), contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Offline AI Coach", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your study history stays on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = { }, label = { Text("LOCAL ANALYSIS · 7 DAYS") })
                Text(advice.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(advice.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Metric("Consistency", "${advice.consistencyPercent}%")
                    Metric("Focus", "${advice.focusMinutes} min")
                    Metric("Break", "${advice.breakMinutes} min")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Load check", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    AssistChip(onClick = { }, label = { Text(advice.fatigueLabel) })
                }
                Text("Suggested daily target: ${advice.dailyFocusMinutes} focus minutes", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Subjects to prioritize", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (advice.neglectedSubjects.isEmpty()) {
            EmptyState("No priority gap detected", "Complete a few focus sessions and the coach will find patterns in your subject history.")
        } else {
            advice.neglectedSubjects.forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.subject.name, fontWeight = FontWeight.Bold)
                        Text("${item.focusMinutes} min in the last 7 days · ${item.pendingTasks} pending tasks", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { (item.focusMinutes / 240f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Text("Suggested blocks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        advice.suggestedBlocks.forEachIndexed { index, block ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(block, modifier = Modifier.weight(1f))
            }
        }
        Text("How it works: the coach scores subjects using recent focus minutes and unfinished tasks, estimates a practical focus interval from completed sessions, and lowers the suggested daily target when recent load is high.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoreScreen(
    sessions: List<com.saad.studymate.data.StudySessionEntity>,
    subjects: List<SubjectEntity>,
    quizzes: List<QuizRecordEntity>,
    notes: List<StudyNoteEntity>,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onAddQuiz: (Long?, String, Double, Double, String) -> Unit,
    onAddNote: (Long?, String, String) -> Unit
) {
    var showQuiz by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    val totalFocus = sessions.sumOf { it.focusMinutes }
    val totalBreak = sessions.sumOf { it.breakMinutes }

    ContentColumn {
        Text("Insights & Vault", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your study records, results and preferences.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Study analytics", fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Total focus", formatMinutes(totalFocus))
                    Metric("Sessions", sessions.size.toString())
                    Metric("Focus days", sessions.filter { it.focusMinutes > 0 }.map { dayOfYear(it.startedAtMillis) }.distinct().size.toString())
                }
                Text("Subject distribution", fontWeight = FontWeight.SemiBold)
                subjects.forEach { subject ->
                    val mins = sessions.filter { it.subjectId == subject.id }.sumOf { it.focusMinutes }
                    val fraction = if (totalFocus == 0) 0f else mins.toFloat() / totalFocus
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(subject.name, style = MaterialTheme.typography.bodySmall)
                            Text(formatMinutes(mins), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(7.dp), color = parseHex(subject.colorHex), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                Text("Recorded break minutes: $totalBreak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CT & Quiz Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = { showQuiz = true }) { Text("+ Result") }
                }
                if (quizzes.isEmpty()) Text("No results saved yet. Add a CT or quiz score to track progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                quizzes.take(8).forEach { quiz ->
                    val subject = subjects.firstOrNull { it.id == quiz.subjectId }?.name ?: "General"
                    val percent = if (quiz.totalMarks > 0) (quiz.obtainedMarks / quiz.totalMarks * 100).roundToInt() else 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(quiz.title, fontWeight = FontWeight.SemiBold)
                            Text("$subject · ${formatDate(quiz.dateMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("$percent%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    if (quiz.mistakeNotes.isNotBlank()) Text("Review: ${quiz.mistakeNotes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Digital notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = { showNote = true }) { Text("+ Note") }
                }
                if (notes.isEmpty()) Text("Your saved notes will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                notes.take(6).forEach { note ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(note.title, fontWeight = FontWeight.SemiBold)
                        Text(note.body, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dark appearance", fontWeight = FontWeight.SemiBold)
                    Text("Dark mode is enabled by default.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }
        }
        Text("StudyMate stores study data on this device. The offline coach does not send your notes, tasks or scores to a server.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    if (showQuiz) AddQuizDialog(subjects, onDismiss = { showQuiz = false }, onSave = { subject, title, score, total, mistakes ->
        onAddQuiz(subject, title, score, total, mistakes)
        showQuiz = false
    })
    if (showNote) AddNoteDialog(subjects, onDismiss = { showNote = false }, onSave = { subject, title, body ->
        onAddNote(subject, title, body)
        showNote = false
    })
}

@Composable
private fun AddTaskDialog(
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long?, Int, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf<Long?>(subjects.firstOrNull()?.id) }
    var priority by remember { mutableIntStateOf(1) }
    var expanded by remember { mutableStateOf(false) }
    var planForTomorrow by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New study task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task or chapter") }, singleLine = true)
                Text("Add this task to", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !planForTomorrow, onClick = { planForTomorrow = false }, label = { Text("Today") })
                    FilterChip(selected = planForTomorrow, onClick = { planForTomorrow = true }, label = { Text("Tomorrow") })
                }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(subjects.firstOrNull { it.id == selectedSubject }?.name ?: "Choose subject") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        subjects.forEach { subject -> DropdownMenuItem(text = { Text(subject.name) }, onClick = { selectedSubject = subject.id; expanded = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Priority")
                    listOf(0, 1, 2).forEach { value ->
                        FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(listOf("Low", "Normal", "High")[value]) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), selectedSubject, priority, startOfDayOffsetMillis(if (planForTomorrow) 1 else 0)) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddWeeklyGoalDialog(
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (Long, Int) -> Unit
) {
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id) }
    var target by remember { mutableStateOf("180") }
    var expanded by remember { mutableStateOf(false) }
    val minutes = target.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set weekly subject target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(subjects.firstOrNull { it.id == subjectId }?.name ?: "Choose subject")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        subjects.forEach { subject ->
                            DropdownMenuItem(text = { Text(subject.name) }, onClick = { subjectId = subject.id; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit) }, label = { Text("Target minutes this week") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = subjectId != null && minutes != null && minutes in 1..3000, onClick = { onSave(subjectId!!, minutes!!) }) { Text("Save target") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddQuizDialog(
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (Long?, String, Double, Double, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var obtained by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var mistakes by remember { mutableStateOf("") }
    var subjectId by remember { mutableStateOf<Long?>(subjects.firstOrNull()?.id) }
    var expanded by remember { mutableStateOf(false) }
    val score = obtained.toDoubleOrNull()
    val totalScore = total.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log CT / Quiz result") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Quiz title / chapter") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(subjects.firstOrNull { it.id == subjectId }?.name ?: "Choose subject") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        subjects.forEach { DropdownMenuItem(text = { Text(it.name) }, onClick = { subjectId = it.id; expanded = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = obtained, onValueChange = { obtained = it }, label = { Text("Obtained") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = total, onValueChange = { total = it }, label = { Text("Total") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = mistakes, onValueChange = { mistakes = it }, label = { Text("Mistakes to review (optional)") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && score != null && totalScore != null && score >= 0 && totalScore > 0 && score <= totalScore,
                onClick = { onSave(subjectId, title.trim(), score!!, totalScore!!, mistakes.trim()) }
            ) { Text("Save result") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddNoteDialog(
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var subjectId by remember { mutableStateOf<Long?>(subjects.firstOrNull()?.id) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New digital note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Note title") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(subjects.firstOrNull { it.id == subjectId }?.name ?: "Choose subject") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        subjects.forEach { DropdownMenuItem(text = { Text(it.name) }, onClick = { subjectId = it.id; expanded = false }) }
                    }
                }
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Write your note") }, minLines = 4)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && body.isNotBlank(), onClick = { onSave(subjectId, title.trim(), body.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: (Long, Boolean) -> Unit, onDelete: ((Long) -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle(task.id, it) })
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.Medium,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text("${if (task.priority == 2) "High priority" else if (task.priority == 0) "Low priority" else "Normal priority"} · ${formatDate(task.dueAtMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onDelete != null) IconButton(onClick = { onDelete(task.id) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ContentColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 980.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun StatCard(label: String, value: String, symbol: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✦", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun startOfWeekSaturdayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) add(Calendar.DAY_OF_MONTH, -1)
}.timeInMillis

private fun startOfDayOffsetMillis(dayOffset: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, dayOffset)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun formatMinutes(minutes: Int): String = if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
private fun formatDate(millis: Long): String = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(millis))
private fun dayOfYear(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_YEAR)
private fun dailyTaskStreak(tasks: List<TaskEntity>): Int {
    val byDay = tasks.groupBy { startOfDate(it.dueAtMillis) }
    val today = startOfTodayMillis()
    val todayTasks = byDay[today].orEmpty()
    val cursor = Calendar.getInstance().apply {
        timeInMillis = if (todayTasks.isNotEmpty() && todayTasks.all { it.isCompleted }) today else startOfDayOffsetMillis(-1)
    }
    var streak = 0
    while (streak < 370) {
        val planned = byDay[startOfDate(cursor.timeInMillis)].orEmpty()
        if (planned.isEmpty() || planned.any { !it.isCompleted }) break
        streak++
        cursor.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}
private fun startOfDate(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
private fun parseHex(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF6EE7C8))
