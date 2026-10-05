package com.galib.step.data.repo

import com.galib.step.data.db.DailySummaryEntity
import com.galib.step.data.db.StepDatabase
import com.galib.step.data.health.StepSensorManager
import com.galib.step.data.prefs.UserPreferences
import com.galib.step.model.DailyStats
import com.galib.step.model.StatSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

class StepRepository(
    private val sensor: StepSensorManager,
    private val db: StepDatabase,
    private val prefs: UserPreferences
) {
    // The activity and the tracking service both stream the hardware sensor;
    // without this lock two coroutines could read the same baseline and apply
    // the same delta twice, inflating the count.
    private val sensorMutex = Mutex()

    fun observeToday(): Flow<DailyStats> {
        val today = LocalDate.now()
        return combine(db.summaryDao().observeDay(today.toEpochDay()), prefs.prefs) { row, p ->
            row?.let {
                DailyStats(
                    date = today,
                    steps = it.steps,
                    goal = p.dailyGoal,
                    source = runCatching { StatSource.valueOf(it.source) }.getOrDefault(StatSource.NONE)
                )
            } ?: DailyStats.empty(today, p.dailyGoal)
        }
    }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailySummaryEntity>> =
        db.summaryDao().observeRange(from.toEpochDay(), to.toEpochDay())

    /**
     * Pulls fresh data from the hardware sensor into Room.
     */
    suspend fun sync() {
        if (sensor.isAvailable) {
            val raw = withTimeoutOrNull(3000.milliseconds) { sensor.rawSteps().firstOrNull() }
            if (raw != null) onSensorRaw(raw)
        }
    }

    /** Feeds one raw cumulative sensor reading through the daily-delta bookkeeping. */
    suspend fun onSensorRaw(raw: Long) = sensorMutex.withLock {
        val p = prefs.snapshot()
        val today = LocalDate.now().toEpochDay()
        val st = prefs.sensorState()

        if (st.lastRaw < 0) { // Save current sensor reading to today's starting value
            prefs.setSensorState(UserPreferences.SensorState(raw, today, 0L))
            return@withLock
        }

        if (st.dayEpoch != today) { // Day rollover, do not count previous delta
            prefs.setSensorState(UserPreferences.SensorState(raw, today, 0L))
            db.summaryDao().upsert(
                DailySummaryEntity(
                    epochDay = today,
                    steps = 0L,
                    goal = p.dailyGoal,
                    source = StatSource.SENSOR.name
                )
            )
            return@withLock
        }

        if (!p.backgroundTracking) { // Do not add anything
            prefs.setSensorState(
                UserPreferences.SensorState(raw, today, st.todaySteps)
            )
            return@withLock
        }

        if (raw < st.lastRaw) { // Do not add entire raw value on counter reset
            prefs.setSensorState(
                UserPreferences.SensorState(raw, today, st.todaySteps)
            )
            return@withLock
        }

        val delta = (raw - st.lastRaw).coerceAtLeast(0L)
        val newState = UserPreferences.SensorState(
            lastRaw = raw,
            dayEpoch = today,
            todaySteps = st.todaySteps + delta
        )
        prefs.setSensorState(newState)

        db.summaryDao().upsert(
            DailySummaryEntity(
                epochDay = today,
                steps = newState.todaySteps,
                goal = p.dailyGoal,
                source = StatSource.SENSOR.name
            )
        )
    }

    /**
     * Live foreground stream: every hardware step event updates today's row
     * instantly.
     */
    suspend fun collectSensorLive() {
        sensor.rawSteps().collect { raw ->
            onSensorRaw(raw)
        }
    }

    suspend fun rebaselineSensor() = sensorMutex.withLock {
        val today = LocalDate.now().toEpochDay()
        val currentRaw = withTimeoutOrNull(3000.milliseconds) {
            sensor.rawSteps().firstOrNull()
        } ?: return@withLock

        val st = prefs.sensorState()
        prefs.setSensorState(
            UserPreferences.SensorState(
                lastRaw = currentRaw,
                dayEpoch = today,
                todaySteps = if (st.dayEpoch == today) st.todaySteps else 0L
            )
        )
    }
}
