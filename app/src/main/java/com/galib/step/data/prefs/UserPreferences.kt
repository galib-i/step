package com.galib.step.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.galib.step.model.ColourStyle
import com.galib.step.model.StepPrefs
import com.galib.step.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "step_prefs")

class UserPreferences(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val dailyGoal = intPreferencesKey("daily_goal")
        val weeklyGoal = intPreferencesKey("weekly_goal")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColour = booleanPreferencesKey("dynamic_color")
        val paletteId = stringPreferencesKey("palette_id")
        val amoled = booleanPreferencesKey("amoled")
        val colourStyle = stringPreferencesKey("color_style")
        val backgroundTracking = booleanPreferencesKey("background_tracking")


        // Step-sensor bookkeeping
        val sensorLastRaw = longPreferencesKey("sensor_last_raw")
        val sensorDayEpoch = longPreferencesKey("sensor_day_epoch")
        val sensorTodaySteps = longPreferencesKey("sensor_today_steps")
    }

    val prefs: Flow<StepPrefs> = context.dataStore.data.map { p ->
        StepPrefs(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            dailyGoal = p[Keys.dailyGoal] ?: 8000,
            weeklyGoal = p[Keys.weeklyGoal] ?: 56000,
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.themeMode] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColour = p[Keys.dynamicColour] ?: false,
            paletteId = p[Keys.paletteId] ?: "tide",
            amoled = p[Keys.amoled] ?: false,
            colourStyle = runCatching { ColourStyle.valueOf(p[Keys.colourStyle] ?: "TONAL_SPOT") }.getOrDefault(ColourStyle.TONAL_SPOT),
            backgroundTracking = p[Keys.backgroundTracking] ?: true
        )
    }

    suspend fun snapshot(): StepPrefs = prefs.first()

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.onboardingDone] = done }
    suspend fun setDailyGoal(goal: Int) = context.dataStore.edit { it[Keys.dailyGoal] = goal.coerceIn(1000, 50000) }
    suspend fun setWeeklyGoal(goal: Int) = context.dataStore.edit { it[Keys.weeklyGoal] = goal.coerceIn(0, 350000) }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColour] = enabled }
    suspend fun setPaletteId(id: String) = context.dataStore.edit { it[Keys.paletteId] = id }
    suspend fun setAmoled(enabled: Boolean) = context.dataStore.edit { it[Keys.amoled] = enabled }
    suspend fun setColourStyle(style: ColourStyle) = context.dataStore.edit { it[Keys.colourStyle] = style.name }
    suspend fun setBackgroundTracking(enabled: Boolean) = context.dataStore.edit { it[Keys.backgroundTracking] = enabled }

    data class SensorState(val lastRaw: Long, val dayEpoch: Long, val todaySteps: Long)

    suspend fun sensorState(): SensorState {
        val p = context.dataStore.data.first()
        return SensorState(
            lastRaw = p[Keys.sensorLastRaw] ?: -1L,
            dayEpoch = p[Keys.sensorDayEpoch] ?: -1L,
            todaySteps = p[Keys.sensorTodaySteps] ?: 0L
        )
    }

    suspend fun setSensorState(state: SensorState) = context.dataStore.edit {
        it[Keys.sensorLastRaw] = state.lastRaw
        it[Keys.sensorDayEpoch] = state.dayEpoch
        it[Keys.sensorTodaySteps] = state.todaySteps
    }
}
