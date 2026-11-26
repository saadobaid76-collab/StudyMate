package com.saad.studymate.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String
)

@Entity(
    tableName = "tasks",
    indices = [Index("dueAtMillis"), Index("subjectId"), Index("isCompleted")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectId: Long?,
    val dueAtMillis: Long,
    val priority: Int = 1,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_sessions",
    indices = [Index("startedAtMillis"), Index("subjectId")]
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long
)

@Entity(
    tableName = "weekly_goals",
    indices = [Index(value = ["subjectId", "weekStartMillis"], unique = true)]
)
data class WeeklyGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val weekStartMillis: Long,
    val targetMinutes: Int,
    val completedMinutes: Int = 0
)

@Entity(tableName = "study_notes", indices = [Index("createdAtMillis")])
data class StudyNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val subjectId: Long?,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_records", indices = [Index("dateMillis"), Index("subjectId")])
data class QuizRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,
    val title: String,
    val obtainedMarks: Double,
    val totalMarks: Double,
    val dateMillis: Long,
    val mistakeNotes: String = ""
)

@Entity(tableName = "reminder_schedules", indices = [Index("triggerAtMillis")])
data class ReminderScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    val repeatMask: Int = 0,
    val enabled: Boolean = true
)
