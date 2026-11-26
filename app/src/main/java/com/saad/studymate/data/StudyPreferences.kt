package com.saad.studymate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studyDataStore by preferencesDataStore(name = "study_preferences")

class StudyPreferences(private val context: Context) {
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val focusMinutesKey = intPreferencesKey("focus_minutes")
    private val shortBreakKey = intPreferencesKey("short_break_minutes")
    private val longBreakKey = intPreferencesKey("long_break_minutes")

    val darkMode: Flow<Boolean> = context.studyDataStore.data.map { it[darkModeKey] ?: true }
    val focusMinutes: Flow<Int> = context.studyDataStore.data.map { it[focusMinutesKey] ?: 25 }
    val shortBreakMinutes: Flow<Int> = context.studyDataStore.data.map { it[shortBreakKey] ?: 5 }
    val longBreakMinutes: Flow<Int> = context.studyDataStore.data.map { it[longBreakKey] ?: 15 }

    suspend fun setDarkMode(enabled: Boolean) {
        context.studyDataStore.edit { it[darkModeKey] = enabled }
    }

    suspend fun setDurations(focus: Int, shortBreak: Int, longBreak: Int) {
        context.studyDataStore.edit {
            it[focusMinutesKey] = focus.coerceIn(1, 180)
            it[shortBreakKey] = shortBreak.coerceIn(1, 60)
            it[longBreakKey] = longBreak.coerceIn(1, 90)
        }
    }
}
