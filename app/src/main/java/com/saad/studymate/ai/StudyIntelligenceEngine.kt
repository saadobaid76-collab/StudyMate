package com.saad.studymate.ai

import com.saad.studymate.data.StudySessionEntity
import com.saad.studymate.data.SubjectEntity
import com.saad.studymate.data.TaskEntity
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Offline study coach. It uses only local task/session history; no network, API key,
 * account, or remote model is required. Recommendations are transparent heuristics,
 * not medical or psychological assessments.
 */
object StudyIntelligenceEngine {
    data class SubjectLoad(
        val subject: SubjectEntity,
        val focusMinutes: Int,
        val pendingTasks: Int,
        val priorityScore: Int
    )

    data class StudyAdvice(
        val focusMinutes: Int,
        val breakMinutes: Int,
        val consistencyPercent: Int,
        val fatigueLabel: String,
        val dailyFocusMinutes: Int,
        val neglectedSubjects: List<SubjectLoad>,
        val headline: String,
        val explanation: String,
        val suggestedBlocks: List<String>
    )

    fun analyze(
        sessions: List<StudySessionEntity>,
        tasks: List<TaskEntity>,
        subjects: List<SubjectEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): StudyAdvice {
        val sevenDaysAgo = nowMillis - 7L * 24L * 60L * 60L * 1000L
        val recent = sessions.filter { it.startedAtMillis >= sevenDaysAgo }
        val focusSessions = recent.filter { it.focusMinutes > 0 }
        val activeDays = focusSessions.map { dayKey(it.startedAtMillis) }.distinct().size
        val consistency = ((activeDays / 7.0) * 100).roundToInt().coerceIn(0, 100)

        val focusLengths = focusSessions.map { it.focusMinutes }.filter { it in 5..180 }
        val recommendedFocus = if (focusLengths.isEmpty()) 35 else {
            focusLengths.average().roundToInt().coerceIn(25, 50)
        }
        val averageBreak = recent.map { it.breakMinutes }.filter { it in 1..60 }
            .takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.coerceIn(5, 12) ?: 7

        val totalMinutes = focusSessions.sumOf { it.focusMinutes.coerceAtLeast(0) }
        val averagePerDay = totalMinutes / 7.0
        val fatigue = when {
            averagePerDay >= 300 -> "High load"
            averagePerDay >= 210 -> "Moderate load"
            else -> "Balanced"
        }
        val dailyTarget = when {
            fatigue == "High load" -> 150
            consistency < 30 -> 90
            consistency < 60 -> 120
            else -> 150
        }

        val subjectLoads = subjects.map { subject ->
            val subjectMinutes = focusSessions.filter { it.subjectId == subject.id }.sumOf { it.focusMinutes }
            val pending = tasks.count { it.subjectId == subject.id && !it.isCompleted }
            val score = (pending * 18 + (120 - subjectMinutes).coerceAtLeast(0) / 4)
                .coerceIn(0, 100)
            SubjectLoad(subject, subjectMinutes, pending, score)
        }.sortedByDescending { it.priorityScore }
        val neglected = subjectLoads.filter { it.priorityScore >= 20 }.take(3)

        val blocks = buildList {
            if (neglected.isNotEmpty()) {
                add("${neglected.first().subject.name} · $recommendedFocus min focus")
            }
            if (neglected.size > 1) add("${neglected[1].subject.name} · $recommendedFocus min focus")
            add("Review notes and solve practice questions · 20 min")
        }

        val headline = when {
            recent.isEmpty() -> "Start with one focused session"
            neglected.isNotEmpty() -> "Give ${neglected.first().subject.name} some attention"
            consistency >= 70 -> "Your study rhythm is building well"
            else -> "A small, consistent plan wins today"
        }
        val explanation = when {
            recent.isEmpty() -> "No recent focus sessions were found. Log a session and the coach will learn from your local study pattern."
            neglected.isNotEmpty() -> "${neglected.first().subject.name} has comparatively low focus time or more unfinished tasks in the last 7 days. Start there, then review what you learned."
            else -> "You have studied on $activeDays of the last 7 days. Keep the next plan manageable and protect your breaks."
        }

        return StudyAdvice(
            focusMinutes = recommendedFocus,
            breakMinutes = averageBreak,
            consistencyPercent = consistency,
            fatigueLabel = fatigue,
            dailyFocusMinutes = dailyTarget,
            neglectedSubjects = neglected,
            headline = headline,
            explanation = explanation,
            suggestedBlocks = blocks
        )
    }

    private fun dayKey(time: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = time }
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }
}
