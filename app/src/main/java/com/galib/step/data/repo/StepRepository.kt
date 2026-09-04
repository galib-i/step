package com.galib.step.data.repo

import com.galib.step.data.db.DailySummaryEntity
import com.galib.step.data.db.StepDatabase
import com.galib.step.data.health.StepSensorManager
import com.galib.step.data.prefs.UserPreferences
import com.galib.step.model.DailyStats
import com.galib.step.model.StatSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.math.max

class StepRepository(
    private val sensor: StepSensorManager,
    private val db: StepDatabase,
    private val prefs: UserPreferences,
    val scope: CoroutineScope
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

    /** Cheap refresh of just the last couple of days — safe to call often. */
    suspend fun syncToday() = sync(historyDays = 2)

    /**
     * Pulls fresh data from the hardware sensor into Room.
     */
    suspend fun sync(historyDays: Long = 90) {
        if (sensor.isAvailable) {
            val raw = withTimeoutOrNull(3000) { sensor.rawSteps().firstOrNull() }
            if (raw != null) onSensorRaw(raw)
        }
    }

    /** Feeds one raw cumulative sensor reading through the daily-delta bookkeeping. */
    suspend fun onSensorRaw(raw: Long) = sensorMutex.withLock {
        val p = prefs.snapshot()
        val today = LocalDate.now().toEpochDay()
        var st = prefs.sensorState()

        if (st.dayEpoch != today) {
            st = UserPreferences.SensorState(
                lastRaw = if (st.lastRaw < 0) raw else st.lastRaw,
                dayEpoch = today,
                todaySteps = 0
            )
        }
        var delta = raw - st.lastRaw
        if (st.lastRaw < 0) delta = 0                  // first reading ever
        if (delta < 0) delta = raw                     // device rebooted; counter restarted
        val newState = UserPreferences.SensorState(raw, today, st.todaySteps + max(delta, 0L))
        prefs.setSensorState(newState)

        val steps = newState.todaySteps
        db.summaryDao().upsert(
            DailySummaryEntity(
                epochDay = today,
                steps = steps,
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
}
