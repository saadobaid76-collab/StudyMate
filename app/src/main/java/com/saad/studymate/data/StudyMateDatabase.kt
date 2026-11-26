package com.saad.studymate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        TaskEntity::class,
        StudySessionEntity::class,
        WeeklyGoalEntity::class,
        StudyNoteEntity::class,
        QuizRecordEntity::class,
        ReminderScheduleEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class StudyMateDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun taskDao(): TaskDao
    abstract fun sessionDao(): StudySessionDao
    abstract fun weeklyGoalDao(): WeeklyGoalDao
    abstract fun noteDao(): StudyNoteDao
    abstract fun quizDao(): QuizRecordDao
    abstract fun reminderDao(): ReminderScheduleDao

    companion object {
        @Volatile private var INSTANCE: StudyMateDatabase? = null

        fun get(context: Context): StudyMateDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                StudyMateDatabase::class.java,
                "studymate.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
