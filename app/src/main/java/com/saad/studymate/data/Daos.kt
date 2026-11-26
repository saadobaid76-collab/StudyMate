package com.saad.studymate.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SubjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SubjectEntity): Long
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueAtMillis ASC, priority DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority DESC, dueAtMillis ASC")
    suspend fun getPending(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE dueAtMillis >= :startMillis AND dueAtMillis < :endMillis ORDER BY dueAtMillis ASC, priority DESC")
    suspend fun getDueBetween(startMillis: Long, endMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE createdAtMillis >= :sinceMillis OR dueAtMillis >= :sinceMillis")
    suspend fun getRecent(sinceMillis: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :taskId")
    suspend fun setCompleted(taskId: Long, completed: Boolean)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun delete(taskId: Long)
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE startedAtMillis >= :sinceMillis ORDER BY startedAtMillis DESC")
    suspend fun getSince(sinceMillis: Long): List<StudySessionEntity>

    @Query("SELECT COALESCE(SUM(focusMinutes), 0) FROM study_sessions WHERE startedAtMillis >= :sinceMillis")
    suspend fun totalFocusSince(sinceMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: StudySessionEntity): Long
}

@Dao
interface WeeklyGoalDao {
    @Query("SELECT * FROM weekly_goals WHERE weekStartMillis = :weekStartMillis")
    fun observeForWeek(weekStartMillis: Long): Flow<List<WeeklyGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(goal: WeeklyGoalEntity): Long

    @Query("UPDATE weekly_goals SET completedMinutes = MIN(targetMinutes, completedMinutes + :minutes) WHERE subjectId = :subjectId AND weekStartMillis = :weekStartMillis")
    suspend fun addProgress(subjectId: Long, weekStartMillis: Long, minutes: Int)
}

@Dao
interface StudyNoteDao {
    @Query("SELECT * FROM study_notes ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<StudyNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: StudyNoteEntity): Long

    @Query("DELETE FROM study_notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface QuizRecordDao {
    @Query("SELECT * FROM quiz_records ORDER BY dateMillis DESC")
    fun observeAll(): Flow<List<QuizRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: QuizRecordEntity): Long
}

@Dao
interface ReminderScheduleDao {
    @Query("SELECT * FROM reminder_schedules WHERE enabled = 1 ORDER BY triggerAtMillis")
    fun observeEnabled(): Flow<List<ReminderScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ReminderScheduleEntity): Long
}
