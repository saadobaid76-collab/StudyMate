package com.saad.studymate

import android.app.Application
import com.saad.studymate.data.StudyMateDatabase
import com.saad.studymate.reminder.DailyGoalReminder
import com.saad.studymate.data.SubjectEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StudyMateApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        DailyGoalReminder.schedule(this)
        appScope.launch {
            val dao = StudyMateDatabase.get(this@StudyMateApplication).subjectDao()
            if (dao.count() == 0) {
                dao.insertAll(
                    listOf(
                        SubjectEntity(name = "Physics", colorHex = "#2979FF"),
                        SubjectEntity(name = "Chemistry", colorHex = "#AA00FF"),
                        SubjectEntity(name = "Higher Math", colorHex = "#FF6D00"),
                        SubjectEntity(name = "Biology", colorHex = "#00C853"),
                        SubjectEntity(name = "English", colorHex = "#00A6A6")
                    )
                )
            }
        }
    }
}
