package com.saad.studymate.ai

import com.saad.studymate.data.StudySessionEntity
import com.saad.studymate.data.SubjectEntity
import com.saad.studymate.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyIntelligenceEngineTest {
    @Test
    fun emptyHistoryProducesSafeStarterPlan() {
        val result = StudyIntelligenceEngine.analyze(emptyList(), emptyList(), emptyList(), 1_700_000_000_000L)
        assertEquals(35, result.focusMinutes)
        assertEquals(7, result.breakMinutes)
        assertTrue(result.dailyFocusMinutes in 60..180)
        assertTrue(result.headline.isNotBlank())
    }

    @Test
    fun pendingAndNeglectedSubjectMovesUpPriorityList() {
        val now = 1_700_000_000_000L
        val physics = SubjectEntity(1, "Physics", "#2979FF")
        val chemistry = SubjectEntity(2, "Chemistry", "#AA00FF")
        val recentSession = StudySessionEntity(
            subjectId = 2,
            focusMinutes = 45,
            breakMinutes = 7,
            startedAtMillis = now - 60_000L,
            endedAtMillis = now
        )
        val tasks = listOf(
            TaskEntity(title = "Physics chapter", subjectId = 1, dueAtMillis = now, isCompleted = false)
        )
        val result = StudyIntelligenceEngine.analyze(listOf(recentSession), tasks, listOf(physics, chemistry), now)
        assertEquals("Physics", result.neglectedSubjects.first().subject.name)
        assertEquals(14, result.consistencyPercent)
    }
}
