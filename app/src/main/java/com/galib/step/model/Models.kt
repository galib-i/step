package com.galib.step.model

import java.time.LocalDate

enum class StatSource { SENSOR, NONE }

data class DailyStats(
    val date: LocalDate,
    val steps: Long,
    val goal: Int,
    val source: StatSource
) {
    val progress: Float get() = if (goal <= 0) 0f else (steps.toFloat() / goal.toFloat())

    companion object {
        fun empty(date: LocalDate = LocalDate.now(), goal: Int = 8000) =
            DailyStats(date, 0L, goal, StatSource.NONE)
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }


data class StepPrefs(
    val onboardingDone: Boolean = false,
    val dailyGoal: Int = 8000,
    val weeklyGoal: Int = 56000,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColour: Boolean = false,
    val amoled: Boolean = false,
    val backgroundTracking: Boolean = false,
    val disableAnimations: Boolean = false
)
