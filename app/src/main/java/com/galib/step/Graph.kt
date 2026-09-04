package com.galib.step

import android.content.Context
import com.galib.step.data.db.StepDatabase
import com.galib.step.data.health.StepSensorManager
import com.galib.step.data.prefs.UserPreferences
import com.galib.step.data.repo.StepRepository
import com.galib.step.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import android.annotation.SuppressLint

/** Tiny hand-rolled service locator — the app is small enough not to need DI. */
@SuppressLint("StaticFieldLeak")
object Graph {
    lateinit var prefs: UserPreferences
        private set
    lateinit var database: StepDatabase
        private set
    lateinit var stepSensor: StepSensorManager
        private set
    lateinit var notifier: Notifier
        private set
    lateinit var repository: StepRepository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            prefs = UserPreferences(app)
            database = StepDatabase.build(app)
            stepSensor = StepSensorManager(app)
            notifier = Notifier(app)
            repository = StepRepository(stepSensor, database, prefs)
            initialized = true
        }
    }
}
